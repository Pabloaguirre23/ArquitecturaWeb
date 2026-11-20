package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.RequestIdentity;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica el corazón de 6.4: dos escaneos concurrentes del mismo QR sobre
 * PostgreSQL real producen exactamente un éxito y un conflicto, sin find+save.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AccessConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private AccessService accessService;

    @BeforeEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM entrada");
        jdbcTemplate.update("DELETE FROM orden_item");
        jdbcTemplate.update("DELETE FROM orden");
    }

    @Test
    void dosEscaneosConcurrentesConsumenLaEntradaUnaSolaVez() throws Exception {
        UUID clienteId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID qrCode = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID entradaId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        OffsetDateTime fechaEvento = OffsetDateTime.parse("2027-03-15T00:00:00Z");

        jdbcTemplate.update("""
                INSERT INTO orden (id, cliente_id, evento_id, evento_nombre, evento_fecha,
                                   reserva_id, estado, total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, ordenId, clienteId, eventoId, "Recital", fechaEvento,
                UUID.randomUUID(), "PAGADA", new BigDecimal("10000.00"));
        jdbcTemplate.update("""
                INSERT INTO orden_item (id, orden_id, sector_id, cantidad, precio_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)
                """, itemId, ordenId, sectorId, 1, new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        jdbcTemplate.update("""
                INSERT INTO entrada (id, orden_id, orden_item_id, cliente_id, evento_id,
                                     evento_nombre, evento_fecha, sector_id, qr_code, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entradaId, ordenId, itemId, clienteId, eventoId, "Recital", fechaEvento,
                sectorId, qrCode, EstadoEntrada.VALIDA.name());

        RequestIdentity identity = new RequestIdentity(staffId, "ACCESS_STAFF");
        AccessValidationRequest request = new AccessValidationRequest(qrCode, eventoId);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(executor.submit(() -> {
                    salida.await();
                    try {
                        accessService.validar(request, identity);
                        return "OK";
                    } catch (ApiException ex) {
                        if (ex.getStatus() == HttpStatus.CONFLICT) {
                            return "CONFLICTO";
                        }
                        return "OTRO:" + ex.getCode();
                    }
                }));
            }
            salida.countDown();
            List<String> estados = new ArrayList<>();
            for (Future<String> future : resultados) {
                estados.add(future.get(10, TimeUnit.SECONDS));
            }
            executor.shutdown();
            assertThat(estados).containsExactlyInAnyOrder("OK", "CONFLICTO");
        } finally {
            executor.shutdownNow();
        }

        var entrada = entradaRepository.findById(entradaId).orElseThrow();
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.UTILIZADA);
        assertThat(entrada.getUsedAt()).isNotNull();
        assertThat(entrada.getUsedBy()).isEqualTo(staffId);

        Integer auditorias = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE operacion = 'INGRESO_VALIDADO' "
                        + "AND entidad_id = ?", Integer.class, entradaId);
        assertThat(auditorias).isEqualTo(1);
    }
}
