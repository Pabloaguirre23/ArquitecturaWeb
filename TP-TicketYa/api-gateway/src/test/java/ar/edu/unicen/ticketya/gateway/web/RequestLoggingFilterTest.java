package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.edu.unicen.ticketya.gateway.TestTokens;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Comprueba que cada request deja un log con correlación, identidad, estado y duración. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestLoggingFilterTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    @Test
    void registraUnaLineaPorRequestConIdentidadCorrelacionYEstado() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String correlacion = client.get().uri("/api/recommendations")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtService, "ORGANIZER"))
                    .exchange()
                    .expectStatus().isForbidden()
                    .returnResult(Void.class)
                    .getResponseHeaders().getFirst("X-Correlation-Id");

            assertThat(correlacion).isNotBlank();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(appender.list).anySatisfy(evento -> {
                        Map<String, String> mdc = evento.getMDCPropertyMap();
                        assertThat(mdc).containsEntry("correlationId", correlacion);
                        assertThat(mdc).containsEntry("userId", TestTokens.ORGANIZER_ID);
                        assertThat(mdc).containsEntry("role", "ORGANIZER");
                        assertThat(mdc).containsEntry("method", "GET");
                        assertThat(mdc).containsEntry("path", "/api/recommendations");
                        assertThat(mdc).containsEntry("status", "403");
                        assertThat(mdc).containsKey("durationMs");
                    }));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
