package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Liberar y confirmar corren a la vez sobre la misma reserva vigente.
 * Gana una sola: o queda confirmada con el cupo vendido, o queda liberada y la zona vuelve entera.
 */
@SpringBootTest
@Testcontainers
class CarreraLiberarConfirmarPostgresTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final int CANTIDAD = 2;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConfirmacionService confirmacionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liberarYConfirmarLaMismaReservaDejanUnSoloResultado() throws Exception {
        int antes = disponibles();
        UUID clienteId = UUID.randomUUID();
        UUID reservaId = reservarZona(clienteId);

        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> confirmacion = pool.submit(() -> {
                barrera.await(5, TimeUnit.SECONDS);
                try {
                    confirmacionService.confirmar(reservaId);
                    return "CONFIRMADA";
                } catch (ApiException error) {
                    return error.getCode();
                }
            });
            Future<String> liberacion = pool.submit(() -> {
                barrera.await(5, TimeUnit.SECONDS);
                try {
                    confirmacionService.liberar(reservaId);
                    return "LIBERADA";
                } catch (ApiException error) {
                    return error.getCode();
                }
            });

            String ganoConfirmar = confirmacion.get(15, TimeUnit.SECONDS);
            String ganoLiberar = liberacion.get(15, TimeUnit.SECONDS);
            String estado = estadoReserva(reservaId);

            if ("CONFIRMADA".equals(estado)) {
                assertThat(ganoConfirmar).isEqualTo("CONFIRMADA");
                assertThat(ganoLiberar).isEqualTo("RESERVATION_NOT_RELEASABLE");
                assertThat(disponibles()).isEqualTo(antes - CANTIDAD);
                assertThat(vendidas(clienteId)).isEqualTo(CANTIDAD);
            } else {
                assertThat(estado).isEqualTo("LIBERADA");
                assertThat(ganoLiberar).isEqualTo("LIBERADA");
                assertThat(ganoConfirmar).isEqualTo("RESERVATION_NOT_CONFIRMABLE");
                assertThat(disponibles()).isEqualTo(antes);
                assertThat(vendidas(clienteId)).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID reservarZona(UUID clienteId) {
        UUID reservaId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reserva (id, evento_id, cliente_id, estado, expires_at)
                VALUES (?, ?, ?, 'VIGENTE', now() + interval '10 minutes')
                """, reservaId, EVENTO, clienteId);
        jdbcTemplate.update("""
                UPDATE sector_inventario
                   SET disponibles = disponibles - ?
                 WHERE evento_id = ? AND sector_id = ?
                """, CANTIDAD, EVENTO, CAMPO);
        jdbcTemplate.update("""
                INSERT INTO reserva_item (reserva_id, sector_id, asiento_id, cantidad)
                VALUES (?, ?, NULL, ?)
                """, reservaId, CAMPO, CANTIDAD);
        return reservaId;
    }

    private String estadoReserva(UUID reservaId) {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    private int vendidas(UUID clienteId) {
        Integer valor = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(entradas_vendidas), 0)::int FROM cliente_evento
                 WHERE evento_id = ? AND cliente_id = ?
                """, Integer.class, EVENTO, clienteId);
        return valor == null ? 0 : valor;
    }

    private int disponibles() {
        Integer valor = jdbcTemplate.queryForObject("""
                SELECT disponibles FROM sector_inventario
                 WHERE evento_id = ? AND sector_id = ?
                """, Integer.class, EVENTO, CAMPO);
        return valor == null ? 0 : valor;
    }
}
