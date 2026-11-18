package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

class QueueRestClientTest {

    private static final String TOKEN = "token-interno-de-prueba";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void levantarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void bajarServidor() {
        MDC.clear();
        server.stop(0);
    }

    @Test
    void abrirLaFilaEnviaElTokenInternoYLaCorrelacion() {
        UUID eventoId = UUID.randomUUID();
        List<String> metodos = new CopyOnWriteArrayList<>();
        Map<String, String> headers = new java.util.concurrent.ConcurrentHashMap<>();
        server.createContext("/internal/events/" + eventoId, exchange -> {
            metodos.add(exchange.getRequestMethod());
            headers.put("X-Internal-Token", exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            headers.put("X-Correlation-Id", exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            responder(exchange, 201, "{}");
        });
        MDC.put(QueueRestClient.CORRELATION_MDC_KEY, "corr-123");

        cliente().abrir(eventoId);

        assertThat(metodos).containsExactly("POST");
        assertThat(headers).containsEntry("X-Internal-Token", TOKEN)
                .containsEntry("X-Correlation-Id", "corr-123");
    }

    @Test
    void cerrarLaFilaEnviaElMotivo() {
        UUID eventoId = UUID.randomUUID();
        List<String> cuerpos = new CopyOnWriteArrayList<>();
        server.createContext("/internal/events/" + eventoId + "/close", exchange -> {
            cuerpos.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(exchange, 200, "{}");
        });

        cliente().cerrar(eventoId, "AGOTADO");

        assertThat(cuerpos).containsExactly("{\"motivo\":\"AGOTADO\"}");
    }

    @Test
    void reintentaAnteErrores5xx() {
        UUID eventoId = UUID.randomUUID();
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/events/" + eventoId, exchange -> {
            responder(exchange, intentos.incrementAndGet() < 3 ? 503 : 200, "{}");
        });

        cliente().abrir(eventoId);

        assertThat(intentos).hasValue(QueueRestClient.MAX_INTENTOS);
    }

    @Test
    void noReintentaErrores4xx() {
        UUID eventoId = UUID.randomUUID();
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/events/" + eventoId, exchange -> {
            intentos.incrementAndGet();
            responder(exchange, 401, "{\"code\":\"UNAUTHORIZED\"}");
        });

        assertThatThrownBy(() -> cliente().abrir(eventoId)).isInstanceOf(RuntimeException.class);
        assertThat(intentos).hasValue(1);
    }

    private QueueRestClient cliente() {
        return new QueueRestClient(RestClient.builder(), baseUrl, TOKEN, 1000, 1000);
    }

    private void responder(HttpExchange exchange, int status, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
