package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.gateway.TestTokens;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica que el gateway tenga las rutas de los servicios y que aplique las reglas de rol
 * del contrato. Las peticiones rechazadas nunca llegan a los servicios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String UUID_CUALQUIERA = "00000000-0000-4000-8000-000000000000";

    @Autowired
    private WebTestClient client;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private JwtService jwtService;

    private String auth(String rol) {
        return TestTokens.bearer(jwtService, rol);
    }

    @Test
    void lasRutasApuntanAlServicioCorrecto() {
        Map<String, URI> rutas = routeLocator.getRoutes()
                .collectMap(Route::getId, Route::getUri)
                .block();

        assertThat(rutas).containsKeys("catalog-service", "inventory-service", "orders-service",
                "recommendation-service", "notification-service", "queue-service");
        assertThat(rutas.get("catalog-service").getPort()).isEqualTo(8081);
        assertThat(rutas.get("inventory-service").getPort()).isEqualTo(8082);
        assertThat(rutas.get("orders-service").getPort()).isEqualTo(8083);
        assertThat(rutas.get("recommendation-service").getPort()).isEqualTo(8084);
        assertThat(rutas.get("notification-service").getPort()).isEqualTo(8085);
        assertThat(rutas.get("queue-service").getPort()).isEqualTo(8086);
    }

    @Test
    void unClienteNoPuedeAprobarDevoluciones() {
        client.post().uri("/api/orders/returns/" + UUID_CUALQUIERA + "/approve")
                .header(HttpHeaders.AUTHORIZATION, auth("CLIENT"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unOrganizadorNoPuedeCancelarEventos() {
        client.post().uri("/api/catalog/events/" + UUID_CUALQUIERA + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unClienteNoPuedePublicarEventos() {
        client.post().uri("/api/catalog/events/" + UUID_CUALQUIERA + "/publish")
                .header(HttpHeaders.AUTHORIZATION, auth("CLIENT"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unOrganizadorNoReserva() {
        client.post().uri("/api/inventory/reservations")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unOrganizadorNoEntraALaFila() {
        client.post().uri("/api/queue/events/" + UUID_CUALQUIERA + "/join")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        client.get().uri("/api/queue/events/" + UUID_CUALQUIERA + "/position")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void reservarSinTokenDevuelve401() {
        client.post().uri("/api/inventory/reservations")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void recomendacionesSinTokenDevuelve401() {
        client.get().uri("/api/recommendations")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unOrganizadorNoVeRecomendaciones() {
        client.get().uri("/api/recommendations")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unOrganizadorNoVeNotificaciones() {
        client.get().uri("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unClienteNoPuedeValidarAccesos() {
        client.post().uri("/api/orders/access/validate")
                .header(HttpHeaders.AUTHORIZATION, auth("CLIENT"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unClienteNoPuedeConsultarReportes() {
        client.get().uri("/api/orders/reports/events/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, auth("CLIENT"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }
}