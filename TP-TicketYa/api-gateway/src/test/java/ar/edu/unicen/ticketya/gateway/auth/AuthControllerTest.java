package ar.edu.unicen.ticketya.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.jsonwebtoken.Claims;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    private WebTestClient.ResponseSpec login(Map<String, String> body) {
        return client.post().uri("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    @Test
    void loginCorrectoDevuelve200ConTokenValido() {
        LoginResponse body = login(Map.of("email", "cliente@ticketya.test", "password", "demo"))
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.role()).isEqualTo("CLIENT");
        assertThat(body.email()).isEqualTo("cliente@ticketya.test");
        assertThat(body.nombre()).isEqualTo("Cliente Demo");
        assertThat(body.expiresAt()).isNotNull();

        Claims claims = jwtService.parse(body.token());
        assertThat(claims.getSubject()).isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENT");
        assertThat(claims.get("email", String.class)).isEqualTo("cliente@ticketya.test");
        assertThat(claims.getExpiration()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "cliente@ticketya.test,CLIENT",
            "organizador@ticketya.test,ORGANIZER",
            "admin@ticketya.test,ADMIN",
            "acceso@ticketya.test,ACCESS_STAFF"
    })
    void cadaUsuarioDemoRecibeSuRol(String email, String rol) {
        login(Map.of("email", email, "password", "demo"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo(rol);
    }

    @Test
    void passwordIncorrectaDevuelve401() {
        login(Map.of("email", "cliente@ticketya.test", "password", "incorrecta"))
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void emailInexistenteDevuelve401() {
        login(Map.of("email", "nadie@ticketya.test", "password", "demo"))
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void sinPasswordDevuelve400() {
        login(Map.of("email", "cliente@ticketya.test"))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("password");
    }

    @Test
    void sinCuerpoDevuelve400() {
        client.post().uri("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}