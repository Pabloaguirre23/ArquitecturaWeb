package ar.edu.unicen.ticketya.orders.servicio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = OrdersResilienciaTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration")
class OrdersResilienciaTest {

    private static final WireMockServer INVENTORY = new WireMockServer(wireMockConfig().dynamicPort());
    private static final UUID RESERVA = UUID.fromString("a1111111-1111-4111-8111-111111111111");
    private static final UUID ORDEN = UUID.fromString("b2222222-2222-4222-8222-222222222222");

    static {
        INVENTORY.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("inventory.url", INVENTORY::baseUrl);
        registry.add("inventory.timeout.connect-ms", () -> "300");
        registry.add("inventory.timeout.read-ms", () -> "300");
        registry.add("resilience4j.retry.instances.inventario.maxAttempts", () -> "3");
        registry.add("resilience4j.retry.instances.inventario.waitDuration", () -> "20ms");
        registry.add("resilience4j.retry.instances.inventario.enableExponentialBackoff", () -> "true");
        registry.add("resilience4j.retry.instances.inventario.exponentialBackoffMultiplier", () -> "2");
        registry.add("resilience4j.retry.instances.inventario.randomizedWaitFactor", () -> "0");
        registry.add("resilience4j.retry.instances.pagos.maxAttempts", () -> "2");
        registry.add("resilience4j.retry.instances.pagos.waitDuration", () -> "20ms");
        registry.add("resilience4j.circuitbreaker.instances.inventario.slidingWindowSize", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.inventario.minimumNumberOfCalls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.inventario.failureRateThreshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.inventario.waitDurationInOpenState", () -> "1s");
        registry.add("resilience4j.circuitbreaker.instances.inventario.permittedNumberOfCallsInHalfOpenState", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.inventario.automaticTransitionFromOpenToHalfOpenEnabled", () -> "true");
        registry.add("resilience4j.circuitbreaker.instances.pagos.slidingWindowSize", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.pagos.minimumNumberOfCalls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.pagos.failureRateThreshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.pagos.waitDurationInOpenState", () -> "1s");
        registry.add("resilience4j.circuitbreaker.instances.pagos.permittedNumberOfCallsInHalfOpenState", () -> "1");
    }

    @Autowired
    private InventoryRestClient inventoryClient;
    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void limpiar() {
        INVENTORY.resetAll();
        circuitBreakers.circuitBreaker(InventoryRestClient.CIRCUITO).reset();
        circuitBreakers.circuitBreaker(MockPaymentGateway.CIRCUITO).reset();
    }

    @AfterAll
    static void cerrar() {
        INVENTORY.stop();
    }

    @Test
    void un5xxDeInventorySeReintentaYElFallbackFallaRapido() {
        INVENTORY.stubFor(post(urlPathMatching("/internal/reservations/.*/confirm"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> inventoryClient.confirmarReserva(RESERVA, "orden:confirmar"))
                .isInstanceOf(InventoryUnavailableException.class)
                .hasMessageContaining("confirmar la reserva");

        INVENTORY.verify(3, postRequestedFor(urlPathMatching("/internal/reservations/.*/confirm")));
    }

    @Test
    void unInventoryLentoCortaPorTimeoutSinBloquearLaCompra() {
        INVENTORY.stubFor(post(urlPathMatching("/internal/reservations/.*/confirm"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> inventoryClient.confirmarReserva(RESERVA, "orden:confirmar"))
                .isInstanceOf(InventoryUnavailableException.class);
        Duration transcurrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(transcurrido).isLessThan(Duration.ofMillis(2_000));
        assertThat(INVENTORY.getAllServeEvents()).isNotEmpty();
    }

    @Test
    void elCircuitoDeInventorySeAbreYSeRecupera() throws Exception {
        INVENTORY.stubFor(post(urlPathMatching("/internal/reservations/.*/confirm"))
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> inventoryClient.confirmarReserva(RESERVA, "orden:confirmar"))
                    .isInstanceOf(InventoryUnavailableException.class);
        }

        CircuitBreaker circuito = circuitBreakers.circuitBreaker(InventoryRestClient.CIRCUITO);
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(meterRegistry.find("resilience4j.circuitbreaker.state")
                .tag("name", InventoryRestClient.CIRCUITO)
                .gauge()).isNotNull();

        INVENTORY.resetRequests();
        long inicio = System.nanoTime();
        assertThatThrownBy(() -> inventoryClient.confirmarReserva(RESERVA, "orden:confirmar"))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(200));
        assertThat(INVENTORY.getAllServeEvents()).isEmpty();

        INVENTORY.resetAll();
        INVENTORY.stubFor(post(urlPathMatching("/internal/reservations/.*/confirm"))
                .willReturn(aResponse().withStatus(200)));
        Thread.sleep(1_200);
        inventoryClient.confirmarReserva(RESERVA, "orden:confirmar");
        inventoryClient.confirmarReserva(RESERVA, "orden:confirmar");

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        INVENTORY.verify(2, postRequestedFor(urlPathMatching("/internal/reservations/.*/confirm")));
    }

    @Test
    void siElCircuitoDePagosEstaAbiertoLaOrdenNoSeCobra() {
        CircuitBreaker pagos = circuitBreakers.circuitBreaker(MockPaymentGateway.CIRCUITO);
        pagos.transitionToOpenState();

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> paymentGateway.cobrar(ORDEN, new BigDecimal("1000.00"),
                MedioPago.TARJETA, "APROBADO", "orden:pago"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("no respondió");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(200));
    }

    @Test
    void unPagoLentoSeReintentaYTerminaEnErrorDePasarela() {
        assertThatThrownBy(() -> paymentGateway.cobrar(ORDEN, new BigDecimal("1000.00"),
                MedioPago.TARJETA, "LENTO", "orden:pago"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("no respondió");
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({InventoryRestClient.class, MockPaymentGateway.class})
    static class App {
    }
}
