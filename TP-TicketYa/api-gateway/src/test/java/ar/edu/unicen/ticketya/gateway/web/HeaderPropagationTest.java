package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.gateway.TestTokens;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Prueba qué headers llegan realmente a los servicios. Un servidor falso hace de catalog,
 * orders y recommendation y guarda los headers de la última petición que recibe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HeaderPropagationTest {

    private static final String UUID_CUALQUIERA = "00000000-0000-4000-8000-000000000000";

    private static final AtomicReference<Headers> RECIBIDOS = new AtomicReference<>();
    private static final HttpServer STUB = crearStub();

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    private static HttpServer crearStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECIBIDOS.set(exchange.getRequestHeaders());
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void rutasAlStub(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + STUB.getAddress().getPort();
        registry.add("CATALOG_URL", () -> url);
        registry.add("ORDERS_URL", () -> url);
        registry.add("RECOMMENDATION_URL", () -> url);
    }

    @AfterAll
    static void cerrarStub() {
        STUB.stop(0);
    }

    @BeforeEach
    void limpiar() {
        RECIBIDOS.set(null);
    }

    @Test
    void laIdentidadFalsificadaSeReemplazaPorLaDelToken() {
        client.get().uri("/api/recommendations")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtService, "CLIENT"))
                .header("X-User-Id", TestTokens.ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "falso@ticketya.test")
                .header("X-Internal-Token", "token-robado")
                .exchange()
                .expectStatus().isOk();

        Headers recibidos = RECIBIDOS.get();
        assertThat(recibidos).isNotNull();
        assertThat(recibidos.getFirst("X-User-Id")).isEqualTo(TestTokens.CLIENT_ID);
        assertThat(recibidos.getFirst("X-User-Role")).isEqualTo("CLIENT");
        assertThat(recibidos.getFirst("X-User-Email")).isEqualTo("cliente@ticketya.test");
        assertThat(recibidos.getFirst("X-Internal-Token")).isNull();
        assertThat(recibidos.getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void unaLecturaPublicaNoPropagaIdentidadNiTokenInterno() {
        client.get().uri("/api/catalog/events")
                .header("X-User-Id", TestTokens.ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "falso@ticketya.test")
                .header("X-Internal-Token", "token-robado")
                .exchange()
                .expectStatus().isOk();

        Headers recibidos = RECIBIDOS.get();
        assertThat(recibidos).isNotNull();
        assertThat(recibidos.getFirst("X-User-Id")).isNull();
        assertThat(recibidos.getFirst("X-User-Role")).isNull();
        assertThat(recibidos.getFirst("X-User-Email")).isNull();
        assertThat(recibidos.getFirst("X-Internal-Token")).isNull();
    }

    @Test
    void elPersonalDeAccesoValidaEntradas() {
        client.post().uri("/api/orders/access/validate")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtService, "ACCESS_STAFF"))
                .exchange()
                .expectStatus().isOk();

        Headers recibidos = RECIBIDOS.get();
        assertThat(recibidos).isNotNull();
        assertThat(recibidos.getFirst("X-User-Id")).isEqualTo(TestTokens.ACCESS_ID);
        assertThat(recibidos.getFirst("X-User-Role")).isEqualTo("ACCESS_STAFF");
    }

    @Test
    void losEndpointsInternosNoSeExponenPorElGateway() {
        client.get().uri("/internal/preferences/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtService, "CLIENT"))
                .exchange()
                .expectStatus().isNotFound();

        client.post().uri("/internal/events/" + UUID_CUALQUIERA + "/cancel")
                .header("X-Internal-Token", "ticketya-internal-dev")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(RECIBIDOS.get()).isNull();
    }
}