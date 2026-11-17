package ar.edu.unicen.ticketya.inventory.servicio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.config.QueueFilaResiliencia;
import com.github.tomakehurst.wiremock.WireMockServer;
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
        classes = QueueFilaClientTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class QueueFilaClientTest {

    private static final WireMockServer QUEUE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000099");

    static {
        QUEUE.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("queue.url", QUEUE::baseUrl);
        registry.add("queue.timeout.connect-ms", () -> "300");
        registry.add("queue.timeout.read-ms", () -> "300");
        registry.add("resilience4j.retry.instances.queue.maxAttempts", () -> "3");
        registry.add("resilience4j.retry.instances.queue.waitDuration", () -> "20ms");
        registry.add("resilience4j.retry.instances.queue.randomizedWaitFactor", () -> "0");
    }

    @Autowired
    private QueueFilaClient queueFilaClient;

    @BeforeEach
    void limpiar() {
        QUEUE.resetAll();
    }

    @AfterAll
    static void cerrar() {
        QUEUE.stop();
    }

    @Test
    void un503SeReintentaYElFallbackNoFallaLaReconciliacion() {
        QUEUE.stubFor(post(urlPathMatching("/internal/events/.*/close"))
                .willReturn(aResponse().withStatus(503)));

        queueFilaClient.cerrarAgotada(EVENTO);

        QUEUE.verify(3, postRequestedFor(urlPathMatching("/internal/events/.*/close"))
                .withHeader("X-Internal-Token", equalTo("ticketya-internal-dev"))
                .withRequestBody(equalToJson("{\"motivo\":\"AGOTADO\"}")));
    }

    @Test
    void unQueueLentoCortaPorTimeout() {
        QUEUE.stubFor(post(urlPathMatching("/internal/events/.*/close"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        long inicio = System.nanoTime();
        queueFilaClient.cerrarAgotada(EVENTO);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void unaFilaQueNuncaSeAbrioNoSeReintenta() {
        QUEUE.stubFor(post(urlPathMatching("/internal/events/.*/close"))
                .willReturn(aResponse().withStatus(404)));

        queueFilaClient.cerrarAgotada(EVENTO);

        QUEUE.verify(1, postRequestedFor(urlPathMatching("/internal/events/.*/close")));
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({QueueFilaClient.class, QueueFilaResiliencia.class})
    static class App {
    }
}
