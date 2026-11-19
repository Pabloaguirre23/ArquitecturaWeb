package ar.edu.unicen.ticketya.orders.migracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unicen.ticketya.orders.dominio.AuditLog;
import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOutbox;
import ar.edu.unicen.ticketya.orders.dominio.EstadoSaga;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.ResultadoAuditoria;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.AuditLogRepository;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OutboxEventRepository;
import ar.edu.unicen.ticketya.orders.repositorio.SagaCompraRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica la migración V5 (tarea 6.1) sobre PostgreSQL real: columnas aditivas,
 * tablas nuevas, índices de reporte y mapeo JPA con ddl-auto=validate.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MigracionV5IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private OrdenItemRepository ordenItemRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private SagaCompraRepository sagaCompraRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM saga_compra");
        jdbcTemplate.update("DELETE FROM reintegro");
        jdbcTemplate.update("DELETE FROM devolucion");
        jdbcTemplate.update("DELETE FROM entrada");
        jdbcTemplate.update("DELETE FROM pago");
        jdbcTemplate.update("DELETE FROM orden_item");
        jdbcTemplate.update("DELETE FROM orden");
    }

    @Test
    void v5AgregaColumnasAditivasTablasEIndicesDeReporte() {
        List<Map<String, Object>> orden = columnas("orden");
        assertThat(orden).extracting(c -> c.get("column_name"))
                .contains("cliente_email", "evento_organizador_id", "sector_nombre",
                        "evento_tipo", "evento_artistas", "evento_ciudad");

        Map<String, Object> artistas = columna(orden, "evento_artistas");
        assertThat(artistas.get("data_type")).isEqualTo("jsonb");
        assertThat(artistas.get("is_nullable")).isEqualTo("NO");
        assertThat(artistas.get("column_default")).isEqualTo("'[]'::jsonb");

        assertThat(columnas("entrada")).extracting(c -> c.get("column_name"))
                .contains("used_at", "used_by");

        assertThat(tablaExiste("saga_compra")).isTrue();
        assertThat(tablaExiste("outbox_event")).isTrue();
        assertThat(tablaExiste("audit_log")).isTrue();

        List<String> indices = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);
        assertThat(indices).contains(
                "idx_entrada_evento_sector_estado",
                "idx_orden_evento_estado",
                "idx_outbox_event_status_next",
                "idx_audit_log_correlacion");
    }

    @Test
    void entidadesNuevasPersistenYCompartenElSnapshotDeLaOrden() {
        UUID clienteId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        OffsetDateTime fechaEvento = OffsetDateTime.parse("2027-03-15T00:00:00Z");

        Orden orden = new Orden();
        orden.setClienteId(clienteId);
        orden.setClienteEmail("cliente@example.com");
        orden.setEventoId(eventoId);
        orden.setEventoOrganizadorId(UUID.randomUUID());
        orden.setEventoNombre("Festival de Rock Tandil 2027");
        orden.setEventoFecha(fechaEvento);
        orden.setSectorNombre("Campo");
        orden.setEventoTipo("FESTIVAL");
        orden.setEventoArtistas("[\"Los Piojos\", \"Callejeros\"]");
        orden.setEventoCiudad("Tandil");
        orden.setReservaId(UUID.randomUUID());
        orden.setEstado(EstadoOrden.PENDIENTE_PAGO);
        orden.setTotal(new BigDecimal("25000.00"));
        ordenRepository.saveAndFlush(orden);

        Orden guardada = ordenRepository.findById(orden.getId()).orElseThrow();
        assertThat(guardada.getClienteEmail()).isEqualTo("cliente@example.com");
        assertThat(guardada.getEventoOrganizadorId()).isNotNull();
        assertThat(guardada.getSectorNombre()).isEqualTo("Campo");
        assertThat(guardada.getEventoTipo()).isEqualTo("FESTIVAL");
        assertThat(guardada.getEventoArtistas()).contains("Los Piojos");
        assertThat(guardada.getEventoCiudad()).isEqualTo("Tandil");

        OrdenItem item = new OrdenItem(orden.getId(), sectorId, UUID.randomUUID(), 1,
                new BigDecimal("25000.00"), new BigDecimal("25000.00"));
        ordenItemRepository.saveAndFlush(item);

        Entrada entrada = new Entrada();
        entrada.setOrdenId(orden.getId());
        entrada.setOrdenItemId(item.getId());
        entrada.setClienteId(clienteId);
        entrada.setEventoId(eventoId);
        entrada.setEventoNombre(orden.getEventoNombre());
        entrada.setEventoFecha(fechaEvento);
        entrada.setSectorId(sectorId);
        entrada.setAsientoId(item.getAsientoId());
        entrada.setQrCode(UUID.randomUUID());
        entrada.setEstado(EstadoEntrada.UTILIZADA);
        entrada.setUsedAt(OffsetDateTime.now());
        entrada.setUsedBy(UUID.randomUUID());
        entradaRepository.saveAndFlush(entrada);

        Entrada usada = entradaRepository.findById(entrada.getId()).orElseThrow();
        assertThat(usada.getUsedAt()).isNotNull();
        assertThat(usada.getUsedBy()).isNotNull();

        SagaCompra saga = new SagaCompra(orden.getId(), PasoSaga.ORDEN_CREADA);
        sagaCompraRepository.saveAndFlush(saga);
        SagaCompra sagaGuardada = sagaCompraRepository.findById(orden.getId()).orElseThrow();
        assertThat(sagaGuardada.getEstado()).isEqualTo(EstadoSaga.EN_PROCESO);
        assertThat(sagaGuardada.getPaso()).isEqualTo(PasoSaga.ORDEN_CREADA);
        assertThat(sagaGuardada.getCreatedAt()).isNotNull();

        OutboxEvent evento = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "orden.pagada",
                orden.getId(), OffsetDateTime.now(), "corr-6.1", 1, "{\"ordenId\":\"" + orden.getId() + "\"}",
                OffsetDateTime.now());
        outboxEventRepository.saveAndFlush(evento);
        OutboxEvent eventoGuardado = outboxEventRepository.findById(evento.getId()).orElseThrow();
        assertThat(eventoGuardado.getStatus()).isEqualTo(EstadoOutbox.PENDING);
        assertThat(eventoGuardado.getPayload()).contains(orden.getId().toString());

        AuditLog auditoria = new AuditLog("INGRESO_VALIDADO", "ENTRADA", entrada.getId(), UUID.randomUUID(),
                "ACCESS_STAFF", ResultadoAuditoria.EXITOSO, "{\"motivo\":\"ok\"}", "corr-6.1");
        auditLogRepository.saveAndFlush(auditoria);

        List<AuditLog> auditorias = auditLogRepository.findByCorrelationIdOrderByCreatedAtAsc("corr-6.1");
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.get(0).getOperacion()).isEqualTo("INGRESO_VALIDADO");
    }

    @Test
    void outboxRechazaEventIdDuplicado() {
        UUID eventId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        outboxEventRepository.saveAndFlush(new OutboxEvent(UUID.randomUUID(), eventId, "orden.pagada",
                ordenId, now, "corr-1", 1, "{}", now));

        assertThatThrownBy(() -> outboxEventRepository.saveAndFlush(new OutboxEvent(UUID.randomUUID(),
                eventId, "orden.pagada", ordenId, now, "corr-2", 1, "{}", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Map<String, Object>> columnas(String tabla) {
        return jdbcTemplate.queryForList("""
                select column_name, data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = 'public' and table_name = ?
                """, tabla);
    }

    private Map<String, Object> columna(List<Map<String, Object>> columnas, String nombre) {
        return columnas.stream()
                .filter(c -> nombre.equals(c.get("column_name")))
                .findFirst()
                .orElseThrow();
    }

    private boolean tablaExiste(String tabla) {
        return jdbcTemplate.queryForObject(
                "select to_regclass('public." + tabla + "') is not null", Boolean.class);
    }
}
