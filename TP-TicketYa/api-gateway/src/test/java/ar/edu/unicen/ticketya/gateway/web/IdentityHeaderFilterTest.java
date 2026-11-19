package ar.edu.unicen.ticketya.gateway.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import ar.edu.unicen.ticketya.gateway.TestTokens;
import ar.edu.unicen.ticketya.gateway.auth.DemoUser;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica el contrato de identidad (JWT) y de errores del gateway sin depender de los servicios.
 * Las peticiones rechazadas por el filtro nunca se rutean, así que no hace falta levantarlos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityHeaderFilterTest {

    private static final String UUID_CUALQUIERA = "00000000-0000-4000-8000-000000000000";
    private static final Map<String, Object> LUGAR = Map.of("nombre", "Estadio", "direccion", "Calle 1",
            "ciudad", "Tandil", "latitud", -37.3, "longitud", -59.1);

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    @Value("${ticketya.security.jwt.secret}")
    private String secret;

    private String auth(String rol) {
        return TestTokens.bearer(jwtService, rol);
    }

    @Test
    void sinTokenDevuelve401EnFormatoRfc7807() {
        client.post().uri("/api/orders")
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("reservaId", UUID_CUALQUIERA, "medioPago", "TARJETA"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.instance").isEqualTo("/api/orders")
                .jsonPath("$.errors").isArray();
    }

    @Test
    void unTokenMalformadoDevuelve401() {
        client.get().uri("/api/orders/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void unTokenVencidoDevuelve401() {
        JwtService vencido = new JwtService(secret, Duration.ofSeconds(-5));
        String token = vencido.issue(new DemoUser(UUID.fromString(TestTokens.CLIENT_ID),
                "cliente@ticketya.test", "Cliente", "CLIENT", "n/a")).token();

        client.get().uri("/api/orders/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("El token expiró");
    }

    @Test
    void unTokenFirmadoConOtraClaveDevuelve401() {
        JwtService ajeno = new JwtService("otra-clave-distinta-de-mas-de-32-bytes-123456", Duration.ofHours(1));
        String token = ajeno.issue(new DemoUser(UUID.fromString(TestTokens.CLIENT_ID),
                "cliente@ticketya.test", "Cliente", "CLIENT", "n/a")).token();

        client.get().uri("/api/orders/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("El token es inválido");
    }

    @Test
    void unRolDesconocidoEnElTokenDevuelve401() {
        client.get().uri("/api/orders/" + UUID_CUALQUIERA)
                .header(HttpHeaders.AUTHORIZATION, auth("SUPERUSER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void losEncabezadosXUserSinTokenYaNoAutentican() {
        client.get().uri("/api/orders/" + UUID_CUALQUIERA)
                .header("X-User-Id", TestTokens.CLIENT_ID)
                .header("X-User-Role", "CLIENT")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unClienteNoPuedeCrearLugares() {
        client.post().uri("/api/catalog/venues")
                .contentType(APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, auth("CLIENT"))
                .bodyValue(LUGAR)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.instance").isEqualTo("/api/catalog/venues");
    }

    @Test
    void unOrganizadorNoPuedeCrearLugares() {
        client.post().uri("/api/catalog/venues")
                .contentType(APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, auth("ORGANIZER"))
                .bodyValue(LUGAR)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }
}