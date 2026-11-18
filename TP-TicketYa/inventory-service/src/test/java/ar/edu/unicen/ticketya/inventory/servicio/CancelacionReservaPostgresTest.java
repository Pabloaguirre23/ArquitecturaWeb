package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import java.util.List;
import java.util.UUID;
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
class CancelacionReservaPostgresTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID PLATEA = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final UUID ASIENTO = UUID.fromString("c0000000-0000-4000-8000-010101000000");

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
    private ConfirmacionService confirmacionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restaurarCupo() {
        jdbcTemplate.update("""
                UPDATE asiento_inventario
                   SET estado = 'LIBRE', reserva_id = NULL, expires_at = NULL
                 WHERE evento_id = ? AND asiento_id = ?
                """, EVENTO, ASIENTO);
        jdbcTemplate.update("""
                UPDATE sector_inventario SET disponibles = capacidad
                 WHERE evento_id = ? AND sector_id = ?
                """, EVENTO, CAMPO);
    }

    @Test
    void elDuenoCancelaYRepetirNoLiberaDosVeces() {
        UUID cliente = UUID.randomUUID();
        int antes = disponibles();
        var reserva = reservaService.reservar(pedidoConAsiento(), cliente);

        reservaService.cancelar(reserva.id(), cliente);

        assertThat(estadoReserva(reserva.id())).isEqualTo("CANCELADA");
        assertThat(estadoAsiento()).isEqualTo("LIBRE");
        assertThat(disponibles()).isEqualTo(antes);

        reservaService.cancelar(reserva.id(), cliente);

        assertThat(estadoReserva(reserva.id())).isEqualTo("CANCELADA");
        assertThat(disponibles()).isEqualTo(antes);
    }

    @Test
    void otroClienteRecibe403YNoLibera() {
        UUID dueno = UUID.randomUUID();
        var reserva = reservaService.reservar(pedidoZona(), dueno);
        int retenidos = disponibles();

        ApiException error = catchThrowableOfType(
                () -> reservaService.cancelar(reserva.id(), UUID.randomUUID()), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(403);
        assertThat(error.getCode()).isEqualTo("NOT_RESERVATION_OWNER");
        assertThat(estadoReserva(reserva.id())).isEqualTo("VIGENTE");
        assertThat(disponibles()).isEqualTo(retenidos);
    }

    @Test
    void unaConfirmadaResponde409YNoTocaLaVenta() {
        UUID cliente = UUID.randomUUID();
        var reserva = reservaService.reservar(pedidoZona(), cliente);
        confirmacionService.confirmar(reserva.id());
        int vendidos = disponibles();

        ApiException error = catchThrowableOfType(
                () -> reservaService.cancelar(reserva.id(), cliente), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("RESERVATION_NOT_CANCELLABLE");
        assertThat(estadoReserva(reserva.id())).isEqualTo("CONFIRMADA");
        assertThat(disponibles()).isEqualTo(vendidos);
    }

    @Test
    void unaVencidaResponde410() {
        UUID cliente = UUID.randomUUID();
        var reserva = reservaService.reservar(pedidoZona(), cliente);
        jdbcTemplate.update("UPDATE reserva SET expires_at = now() - interval '1 minute' WHERE id = ?", reserva.id());

        ApiException error = catchThrowableOfType(
                () -> reservaService.cancelar(reserva.id(), cliente), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(estadoReserva(reserva.id())).isEqualTo("VENCIDA");
    }

    private ReservaRequest pedidoZona() {
        return new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(CAMPO, null, 1)));
    }

    private ReservaRequest pedidoConAsiento() {
        return new ReservaRequest(EVENTO, List.of(
                new ReservaRequest.Item(PLATEA, List.of(ASIENTO), null),
                new ReservaRequest.Item(CAMPO, null, 1)));
    }

    private int disponibles() {
        return jdbcTemplate.queryForObject(
                "SELECT disponibles FROM sector_inventario WHERE evento_id = ? AND sector_id = ?",
                Integer.class, EVENTO, CAMPO);
    }

    private String estadoReserva(UUID reservaId) {
        return jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    private String estadoAsiento() {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM asiento_inventario WHERE evento_id = ? AND asiento_id = ?",
                String.class, EVENTO, ASIENTO);
    }
}
