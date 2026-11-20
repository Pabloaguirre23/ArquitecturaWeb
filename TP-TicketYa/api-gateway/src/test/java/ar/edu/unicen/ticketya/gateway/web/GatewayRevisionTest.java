package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.gateway.TestTokens;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Rutas públicas, roles del contrato, default-deny y correlation id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRevisionTest {

    private static final String ID = "00000000-0000-4000-8000-000000000000";

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    private WebTestClient.ResponseSpec llamar(String metodo, String ruta, String rol) {
        return client.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtService, rol))
                .exchange();
    }

    @Test
    void listarSectoresEsPublicoSinIdentidad() {
        client.get().uri("/api/catalog/venues/" + ID + "/sectors")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void detalleDeLugarEsPublicoSinIdentidad() {
        client.get().uri("/api/catalog/venues/" + ID)
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void crearSectorSigueProtegido() {
        client.post().uri("/api/catalog/venues/" + ID + "/sectors")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/catalog/events",
            "PUT,/api/catalog/events/00000000-0000-4000-8000-000000000000",
            "PUT,/api/catalog/events/00000000-0000-4000-8000-000000000000/prices/00000000-0000-4000-8000-000000000000",
            "POST,/api/catalog/events/00000000-0000-4000-8000-000000000000/publish"
    })
    void soloUnOrganizadorOperaEventos(String metodo, String ruta) {
        llamar(metodo, ruta, "ADMIN").expectStatus().isForbidden();
        llamar(metodo, ruta, "CLIENT").expectStatus().isForbidden();
        llamar(metodo, ruta, "ORGANIZER")
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void unaRutaInventadaSeDeniega() {
        llamar("GET", "/api/catalog/inventado", "CLIENT").expectStatus().isForbidden();
    }

    @Test
    void unaLecturaPublicaConSlashFinalNoExigeToken() {
        client.get().uri("/api/catalog/events/")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void unUuidDeformeNoMatcheaNingunaRegla() {
        llamar("PUT", "/api/catalog/events/" + "-".repeat(36), "ORGANIZER")
                .expectStatus().isForbidden();
    }

    @Test
    void unCorrelationIdInvalidoSeReemplaza() {
        client.post().uri("/api/orders")
                .header("X-Correlation-Id", "malo")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value("X-Correlation-Id", id -> {
                    assertThat(id).isNotEqualTo("malo");
                    assertThat(UUID.fromString(id)).isNotNull();
                });
    }

    @Test
    void unCorrelationIdValidoSeConserva() {
        client.post().uri("/api/orders")
                .header("X-Correlation-Id", ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", ID);
    }
}