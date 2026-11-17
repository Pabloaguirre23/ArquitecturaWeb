package ar.edu.unicen.ticketya.inventory.servicio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.config.CatalogResiliencia;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest(
        classes = CatalogResilienciaTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class CatalogResilienciaTest {

    private static final WireMockServer CATALOG = new WireMockServer(wireMockConfig().dynamicPort());
    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000099");

    static {
        CATALOG.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("catalog.url", CATALOG::baseUrl);
        registry.add("catalog.timeout.connect-ms", () -> "300");
        registry.add("catalog.timeout.read-ms", () -> "300");
        registry.add("resilience4j.retry.instances.catalog.maxAttempts", () -> "3");
        registry.add("resilience4j.retry.instances.catalog.waitDuration", () -> "20ms");
        registry.add("resilience4j.retry.instances.catalog.enableExponentialBackoff", () -> "true");
        registry.add("resilience4j.retry.instances.catalog.exponentialBackoffMultiplier", () -> "2");
        registry.add("resilience4j.retry.instances.catalog.randomizedWaitFactor", () -> "0");
        registry.add("resilience4j.circuitbreaker.instances.catalog.slidingWindowSize", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.catalog.minimumNumberOfCalls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.catalog.failureRateThreshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.catalog.waitDurationInOpenState", () -> "1s");
        registry.add("resilience4j.circuitbreaker.instances.catalog.permittedNumberOfCallsInHalfOpenState", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.catalog.automaticTransitionFromOpenToHalfOpenEnabled", () -> "true");
    }

    @Autowired
    private CatalogClient catalogClient;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void limpiar() {
        CATALOG.resetAll();
        circuitBreakers.circuitBreaker(CatalogClient.CIRCUITO).reset();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterAll
    static void cerrar() {
        CATALOG.stop();
    }

    @Test
    void un503SeReintentaYElFallbackNoFallaLaOperacion() {
        CATALOG.stubFor(patch(urlPathMatching("/internal/events/.*/availability"))
                .willReturn(aResponse().withStatus(503)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-503");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ListAppender<ILoggingEvent> logs = escucharFallback();

        catalogClient.notificarDisponibilidad(EVENTO, true);

        CATALOG.verify(3, patchRequestedFor(urlPathMatching("/internal/events/.*/availability"))
                .withHeader("X-Correlation-Id", equalTo("corr-503")));
        assertThat(logs.list).anyMatch(evento ->
                evento.getFormattedMessage().contains("corr-503")
                        && evento.getFormattedMessage().contains("Fallback de catalog"));
    }

    @Test
    void unCatalogLentoCortaPorTimeout() {
        CATALOG.stubFor(patch(urlPathMatching("/internal/events/.*/availability"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        long inicio = System.nanoTime();
        catalogClient.notificarDisponibilidad(EVENTO, false);
        Duration transcurrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(transcurrido).isLessThan(Duration.ofMillis(1_500));
        assertThat(CATALOG.getAllServeEvents()).isNotEmpty();
    }

    @Test
    void elCircuitoSeAbreYSeRecuperaCuandoCatalogResponde() throws Exception {
        CATALOG.stubFor(patch(urlPathMatching("/internal/events/.*/availability"))
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 3; i++) {
            catalogClient.notificarDisponibilidad(EVENTO, true);
        }
        CircuitBreaker circuito = circuitBreakers.circuitBreaker(CatalogClient.CIRCUITO);
        assertThat(circuito.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isLessThanOrEqualTo(1000L);
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(meterRegistry.find("resilience4j.circuitbreaker.state")
                .tag("name", "catalog")
                .gauge()).isNotNull();

        CATALOG.resetRequests();
        long inicio = System.nanoTime();
        catalogClient.notificarDisponibilidad(EVENTO, true);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(200));
        assertThat(CATALOG.getAllServeEvents()).isEmpty();

        CATALOG.resetAll();
        CATALOG.stubFor(patch(urlPathMatching("/internal/events/.*/availability"))
                .willReturn(aResponse().withStatus(200)));
        Thread.sleep(1_200);
        catalogClient.notificarDisponibilidad(EVENTO, false);
        catalogClient.notificarDisponibilidad(EVENTO, false);

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        CATALOG.verify(2, patchRequestedFor(urlPathMatching("/internal/events/.*/availability")));
    }

    private static ListAppender<ILoggingEvent> escucharFallback() {
        Logger logger = (Logger) LoggerFactory.getLogger(CatalogClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CatalogClient.class, CatalogResiliencia.class})
    static class App {
    }
}
