package ar.edu.unicen.ticketya.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Primer flujo del módulo de integración (tarea 10.1):
 * gateway arriba y búsqueda pública de catalog a través del gateway.
 *
 * Es un walking skeleton: no requiere Testcontainers porque los servicios se
 * levantan con Maven y las bases con docker-compose.dev.yml. Se salta si no se
 * define TICKETYA_E2E=true, para no romper el build local sin entorno.
 */
@EnabledIfEnvironmentVariable(named = "TICKETYA_E2E", matches = "true")
class GatewayCatalogIT {

    private static final String BASE_URL = System.getenv()
            .getOrDefault("TICKETYA_GATEWAY_URL", "http://localhost:8080");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void elGatewayReportaSalud() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("UP");
    }

    @Test
    void buscaEventosPublicosPorElGateway() throws Exception {
        HttpResponse<String> response = get("/api/catalog/events?page=0&size=5&orden=FECHA_ASC");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("content").isArray()).isTrue();
        assertThat(body.path("page").asInt()).isZero();
        assertThat(body.path("size").asInt()).isEqualTo(5);
        assertThat(body.has("totalElements")).isTrue();
        assertThat(body.has("totalPages")).isTrue();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
