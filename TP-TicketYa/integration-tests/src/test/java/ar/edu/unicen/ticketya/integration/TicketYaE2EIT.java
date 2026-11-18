package ar.edu.unicen.ticketya.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Flujo de punta a punta de la tarea 10.1 por el gateway:
 * identidad → búsqueda, fila → reserva, compra → QR, acceso QR y
 * compra → notificación/recomendaciones.
 *
 * <p>Los tests están ordenados porque comparten el escenario (lugar, evento de alta
 * demanda, reserva y orden). Cada paso usa la API pública por el puerto 8080; no hay
 * mocks de servicios. La suite se salta si no se define {@code TICKETYA_E2E=true} y
 * el acceso QR se marca como pendiente mientras la tarea 6.4 no esté expuesta.</p>
 *
 * <p>Requisitos del entorno: los siete servicios y las bases del compose integral.
 * Para no esperar tandas de 30 s, arrancar queue-service con
 * {@code QUEUE_BATCH_SIZE=1 QUEUE_INTERVAL_SECONDS=2 QUEUE_SCHEDULER_MS=500}.</p>
 */
@EnabledIfEnvironmentVariable(named = "TICKETYA_E2E", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketYaE2EIT {

    private static final String BASE_URL = System.getenv()
            .getOrDefault("TICKETYA_GATEWAY_URL", "http://localhost:8080");
    private static final long TIMEOUT_MS = Long.parseLong(System.getenv()
            .getOrDefault("TICKETYA_E2E_TIMEOUT_MS", "45000"));
    private static final long POLL_MS = 500;

    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String ORGANIZER = "22222222-2222-4222-8222-222222222222";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";
    private static final String STAFF = "44444444-4444-4444-8444-444444444444";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    private static UUID lugarId;
    private static UUID sectorId;
    private static UUID eventoId;
    private static UUID asientoId;
    private static UUID reservaId;
    private static UUID ordenId;
    private static String qrCode;

    @Test
    @Order(1)
    void laBusquedaPublicaNoRequiereIdentidad() throws Exception {
        JsonNode body = ok("GET", "/api/catalog/events?page=0&size=5&orden=FECHA_ASC", Map.of(), null);

        assertThat(body.path("content").isArray()).isTrue();
        assertThat(body.has("totalElements")).isTrue();
    }

    @Test
    @Order(2)
    void laIdentidadEsObligatoriaEnLosEndpointsProtegidos() throws Exception {
        HttpResponse<String> sinIdentidad = send("POST", "/api/inventory/reservations", Map.of(),
                Map.of("eventoId", UUID.randomUUID(), "items", List.of()));
        assertThat(sinIdentidad.statusCode()).isEqualTo(401);
        assertThat(json(sinIdentidad).path("code").asText()).isEqualTo("UNAUTHORIZED");

        HttpResponse<String> rolIncorrecto = send("GET", "/api/recommendations", headers(ORGANIZER, "ORGANIZER"), null);
        assertThat(rolIncorrecto.statusCode()).isEqualTo(403);
        assertThat(json(rolIncorrecto).path("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    @Order(3)
    void laFilaHabilitaLaReservaDeUnEventoDeAltaDemanda() throws Exception {
        crearEventoDeAltaDemanda();

        JsonNode asientos = ok("GET", "/api/inventory/events/" + eventoId + "/seats?sectorId=" + sectorId,
                Map.of(), null);
        JsonNode libre = firstSeat(asientos, "LIBRE");
        asientoId = UUID.fromString(libre.path("asientoId").asText());

        // Sin token de fila, inventory rechaza la reserva del evento de alta demanda.
        HttpResponse<String> sinToken = send("POST", "/api/inventory/reservations", headers(CLIENT, "CLIENT"),
                reservaBody());
        assertThat(sinToken.statusCode()).isEqualTo(403);
        assertThat(json(sinToken).path("code").asText()).isEqualTo("QUEUE_REQUIRED");

        JsonNode ingreso = ok("POST", "/api/queue/events/" + eventoId + "/join", headers(CLIENT, "CLIENT"), null);
        assertThat(ingreso.path("posicion").asInt()).isPositive();

        JsonNode habilitado = await(() -> send("GET", "/api/queue/events/" + eventoId + "/position",
                        headers(CLIENT, "CLIENT"), null),
                body -> "HABILITADO".equals(body.path("estado").asText()),
                "La fila no habilitó al cliente; revisar QUEUE_BATCH_SIZE/QUEUE_INTERVAL_SECONDS del entorno");
        String queueToken = habilitado.path("token").asText();
        assertThat(queueToken).isNotBlank();

        JsonNode reserva = ok("POST", "/api/inventory/reservations",
                merge(headers(CLIENT, "CLIENT"), Map.of("X-Queue-Token", queueToken)), reservaBody());
        reservaId = UUID.fromString(reserva.path("id").asText());

        assertThat(reserva.path("estado").asText()).isEqualTo("VIGENTE");
        assertThat(reserva.path("eventoId").asText()).isEqualTo(eventoId.toString());
        assertThat(reserva.path("items")).hasSize(1);
    }

    @Test
    @Order(4)
    void laCompraGeneraUnaEntradaConQrYEsIdempotente() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, String> headers = merge(headers(CLIENT, "CLIENT"), Map.of("Idempotency-Key", idempotencyKey));
        Map<String, Object> body = Map.of(
                "reservaId", reservaId,
                "medioPago", "TARJETA",
                "token", "APROBADO");

        JsonNode orden = ok("POST", "/api/orders", headers, body);
        ordenId = UUID.fromString(orden.path("id").asText());

        assertThat(orden.path("estado").asText()).isEqualTo("PAGADA");
        assertThat(orden.path("entradas")).hasSize(1);
        qrCode = orden.path("entradas").get(0).path("qrCode").asText();
        assertThat(qrCode).isNotBlank();

        HttpResponse<String> repetida = send("POST", "/api/orders", headers, body);
        assertThat(repetida.statusCode()).isIn(200, 201);
        assertThat(json(repetida).path("id").asText()).isEqualTo(ordenId.toString());
    }

    @Test
    @Order(5)
    void elPersonalDeAccesoValidaElQrUnaSolaVez() throws Exception {
        Map<String, Object> body = Map.of("qrCode", qrCode, "eventoId", eventoId);
        HttpResponse<String> primera = send("POST", "/api/orders/access/validate",
                headers(STAFF, "ACCESS_STAFF"), body);

        Assumptions.assumeTrue(primera.statusCode() != 401 && primera.statusCode() != 403
                        && primera.statusCode() != 404 && primera.statusCode() != 405,
                "Tarea 6.4 pendiente: /api/orders/access/validate todavía no está expuesta");

        assertThat(primera.statusCode()).isEqualTo(200);
        HttpResponse<String> segunda = send("POST", "/api/orders/access/validate",
                headers(STAFF, "ACCESS_STAFF"), body);
        assertThat(segunda.statusCode()).isEqualTo(409);
    }

    @Test
    @Order(6)
    void laCompraAlimentaNotificacionesYRecomendaciones() throws Exception {
        JsonNode notificaciones = await(() -> send("GET", "/api/notifications?size=50", headers(CLIENT, "CLIENT"), null),
                body -> hasNotification(body, "orden.pagada"),
                "No apareció la notificación de orden.pagada; revisar RabbitMQ y el outbox de orders");

        JsonNode notificacion = firstNotification(notificaciones, "orden.pagada");
        assertThat(notificacion.path("estado").asText()).isIn("PENDIENTE", "ENVIADA");

        JsonNode recomendaciones = ok("GET", "/api/recommendations", headers(CLIENT, "CLIENT"), null);
        assertThat(recomendaciones.path("clienteId").asText()).isEqualTo(CLIENT);
        assertThat(recomendaciones.path("recomendaciones").isArray()).isTrue();

        JsonNode similares = ok("GET", "/api/recommendations/events/" + eventoId + "/similar",
                headers(CLIENT, "CLIENT"), null);
        assertThat(similares.path("eventoId").asText()).isEqualTo(eventoId.toString());
        assertThat(similares.path("similares").isArray()).isTrue();
    }

    private void crearEventoDeAltaDemanda() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        JsonNode lugar = ok("POST", "/api/catalog/venues", headers(ADMIN, "ADMIN"), Map.of(
                "nombre", "E2E " + sufijo,
                "direccion", "Calle de prueba 123",
                "ciudad", "Tandil",
                "latitud", -37.3217,
                "longitud", -59.1332));
        lugarId = UUID.fromString(lugar.path("id").asText());

        JsonNode sector = ok("POST", "/api/catalog/venues/" + lugarId + "/sectors", headers(ADMIN, "ADMIN"), Map.of(
                "nombre", "Platea " + sufijo,
                "tipo", "NUMERADO",
                "capacidad", 4,
                "filas", 2,
                "asientosPorFila", 2));
        sectorId = UUID.fromString(sector.path("id").asText());

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("nombre", "Evento E2E " + sufijo);
        evento.put("descripcion", "Escenario de integración");
        evento.put("tipo", "RECITAL");
        evento.put("lugarId", lugarId);
        evento.put("fecha", OffsetDateTime.now(ZoneOffset.ofHours(-3)).plusDays(30).withNano(0).toString());
        evento.put("participantes", List.of("Artista E2E"));
        evento.put("limitePorCliente", 4);
        evento.put("altaDemanda", true);

        JsonNode creado = ok("POST", "/api/catalog/events", headers(ORGANIZER, "ORGANIZER"), evento);
        eventoId = UUID.fromString(creado.path("id").asText());

        ok("PUT", "/api/catalog/events/" + eventoId + "/prices/" + sectorId, headers(ORGANIZER, "ORGANIZER"),
                Map.of("precio", 15000));
        JsonNode publicado = ok("POST", "/api/catalog/events/" + eventoId + "/publish",
                headers(ORGANIZER, "ORGANIZER"), null);
        assertThat(publicado.path("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(publicado.path("altaDemanda").asBoolean()).isTrue();
    }

    private Map<String, Object> reservaBody() {
        return Map.of("eventoId", eventoId, "items", List.of(Map.of("sectorId", sectorId,
                "asientoIds", List.of(asientoId))));
    }

    private JsonNode firstSeat(JsonNode asientos, String estado) {
        for (JsonNode asiento : asientos) {
            if (estado.equals(asiento.path("estado").asText())) {
                return asiento;
            }
        }
        fail("No hay asientos en estado " + estado + " para el sector " + sectorId);
        return null;
    }

    private boolean hasNotification(JsonNode body, String tipo) {
        for (JsonNode item : body.path("content")) {
            if (tipo.equals(item.path("tipo").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode firstNotification(JsonNode body, String tipo) {
        for (JsonNode item : body.path("content")) {
            if (tipo.equals(item.path("tipo").asText())) {
                return item;
            }
        }
        fail("No se encontró una notificación de tipo " + tipo);
        return null;
    }

    private Map<String, String> headers(String userId, String role) {
        return Map.of("X-User-Id", userId, "X-User-Role", role);
    }

    private Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(extra);
        return merged;
    }

    private JsonNode ok(String method, String path, Map<String, String> headers, Object body) throws Exception {
        HttpResponse<String> response = send(method, path, headers, body);
        if (response.statusCode() / 100 != 2) {
            fail(method + " " + path + " esperaba 2xx y devolvió " + response.statusCode() + ": " + response.body());
        }
        return json(response);
    }

    private JsonNode await(Request request, Predicate<JsonNode> condition, String message) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = request.send();
            if (response.statusCode() == 200) {
                last = json(response);
                if (condition.test(last)) {
                    return last;
                }
            }
            Thread.sleep(POLL_MS);
        }
        fail(message + (last == null ? " (sin respuesta 200)" : " (última respuesta: " + last + ")"));
        return null;
    }

    @FunctionalInterface
    private interface Request {
        HttpResponse<String> send() throws Exception;
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> headers, Object body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body());
    }
}
