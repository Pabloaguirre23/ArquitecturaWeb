package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TokenFilaPostgresTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final String SECRETO = "ticketya-queue-dev-secret-key-32b";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void apagarAltaDemanda() {
        jdbcTemplate.update("UPDATE evento_inventario SET alta_demanda = false WHERE evento_id = ?", EVENTO);
    }

    @Test
    void sinTokenVencidoOAjenoResponde403YNoTocaElCupo() {
        marcarAltaDemanda();
        UUID cliente = UUID.randomUUID();
        int antes = disponibles();

        ApiException sinToken = reservar(cliente, null);
        assertThat(sinToken.getStatus().value()).isEqualTo(403);
        assertThat(sinToken.getCode()).isEqualTo("QUEUE_REQUIRED");

        ApiException vencido = reservar(cliente, token(EVENTO, cliente, Instant.now().minusSeconds(5), clave()));
        assertThat(vencido.getCode()).isEqualTo("QUEUE_TOKEN_INVALID");

        ApiException otroCliente = reservar(cliente, token(EVENTO, UUID.randomUUID(), Instant.now().plusSeconds(300), clave()));
        assertThat(otroCliente.getCode()).isEqualTo("QUEUE_TOKEN_INVALID");

        ApiException otroEvento = reservar(cliente, token(UUID.randomUUID(), cliente, Instant.now().plusSeconds(300), clave()));
        assertThat(otroEvento.getCode()).isEqualTo("QUEUE_TOKEN_INVALID");

        SecretKey otra = Keys.hmacShaKeyFor("otra-clave-distinta-de-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        ApiException firma = reservar(cliente, token(EVENTO, cliente, Instant.now().plusSeconds(300), otra));
        assertThat(firma.getCode()).isEqualTo("QUEUE_TOKEN_INVALID");

        assertThat(disponibles()).isEqualTo(antes);
    }

    @Test
    void unTokenVigenteReservaYGuardaElJti() {
        marcarAltaDemanda();
        UUID cliente = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        String jwt = token(EVENTO, cliente, jti, Instant.now().plusSeconds(300), clave());
        int antes = disponibles();

        var respuesta = reservaService.reservar(pedido(), cliente, jwt);

        assertThat(respuesta.id()).isNotNull();
        assertThat(disponibles()).isEqualTo(antes - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT queue_jti FROM reserva WHERE id = ?", UUID.class, respuesta.id())).isEqualTo(jti);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT origen FROM reserva WHERE id = ?", String.class, respuesta.id())).isEqualTo("FILA");
    }

    @Test
    void unEventoNormalNoExigeToken() {
        UUID cliente = UUID.randomUUID();

        var respuesta = reservaService.reservar(pedido(), cliente, null);

        assertThat(respuesta.id()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT queue_jti FROM reserva WHERE id = ?", UUID.class, respuesta.id())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT origen FROM reserva WHERE id = ?", String.class, respuesta.id())).isEqualTo("DIRECTA");
    }

    private ApiException reservar(UUID cliente, String jwt) {
        ApiException error = catchThrowableOfType(
                () -> reservaService.reservar(pedido(), cliente, jwt), ApiException.class);
        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(403);
        return error;
    }

    private void marcarAltaDemanda() {
        jdbcTemplate.update("UPDATE evento_inventario SET alta_demanda = true WHERE evento_id = ?", EVENTO);
    }

    private int disponibles() {
        return jdbcTemplate.queryForObject(
                "SELECT disponibles FROM sector_inventario WHERE evento_id = ? AND sector_id = ?",
                Integer.class, EVENTO, CAMPO);
    }

    private static ReservaRequest pedido() {
        return new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(CAMPO, null, 1)));
    }

    private static SecretKey clave() {
        return Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(UUID evento, UUID cliente, Instant expira, SecretKey clave) {
        return token(evento, cliente, UUID.randomUUID(), expira, clave);
    }

    private static String token(UUID evento, UUID cliente, UUID jti, Instant expira, SecretKey clave) {
        return Jwts.builder()
                .subject(cliente.toString())
                .id(jti.toString())
                .claim("eventId", evento.toString())
                .claim("type", "QUEUE")
                .expiration(Date.from(expira))
                .signWith(clave)
                .compact();
    }
}
