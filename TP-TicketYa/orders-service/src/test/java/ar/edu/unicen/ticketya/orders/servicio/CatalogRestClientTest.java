package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class CatalogRestClientTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void obtenerEventoMapeaLosCamposDelSnapshot() throws IOException {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        String body = """
                {
                  "id": "%s",
                  "nombre": "Recital",
                  "tipo": "SHOW",
                  "ciudad": "Tandil",
                  "organizadorId": "%s",
                  "participantes": ["Los Piojos", "Divididos"],
                  "sectores": [
                    {"sectorId": "%s", "nombre": "Campo", "precio": 15000, "disponibles": 100}
                  ],
                  "campoNuevoQueOrdersNoConoce": "se ignora"
                }
                """.formatted(eventoId, organizadorId, sectorId);
        responder(body);

        EventoSnapshot snapshot = client().obtenerEvento(eventoId);

        assertThat(snapshot.tipo()).isEqualTo("SHOW");
        assertThat(snapshot.ciudad()).isEqualTo("Tandil");
        assertThat(snapshot.organizadorId()).isEqualTo(organizadorId);
        assertThat(snapshot.artistas()).containsExactly("Los Piojos", "Divididos");
        assertThat(snapshot.sectores()).containsEntry(sectorId, "Campo");
    }

    @Test
    void obtenerEventoRespetaTimeoutDeLectura() {
        server.createContext("/api/catalog/events/", exchange -> {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> client().obtenerEvento(UUID.randomUUID()))
                .isInstanceOf(ResourceAccessException.class);
    }

    private CatalogRestClient client() {
        return new CatalogRestClient(RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort());
    }

    private void responder(String body) throws IOException {
        server.createContext("/api/catalog/events/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(HttpStatus.OK.value(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
