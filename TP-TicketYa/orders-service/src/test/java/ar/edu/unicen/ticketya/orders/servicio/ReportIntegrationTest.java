package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.web.RequestIdentity;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import ar.edu.unicen.ticketya.orders.web.dto.SectorReportResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Reporte sobre datos reales de orders con catalog simulado (tarea 6.5). */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportService reportService;

    @MockitoBean
    private CatalogReportClient catalogReportClient;

    @BeforeEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM reintegro");
        jdbcTemplate.update("DELETE FROM devolucion");
        jdbcTemplate.update("DELETE FROM entrada");
        jdbcTemplate.update("DELETE FROM pago");
        jdbcTemplate.update("DELETE FROM orden_item");
        jdbcTemplate.update("DELETE FROM orden");
    }

    @Test
    void reporteReflejaDevolucionAprobadaYReintegro() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID pagoId = UUID.randomUUID();
        UUID entradaVigente = UUID.randomUUID();
        UUID entradaDevuelta = UUID.randomUUID();
        UUID devolucionId = UUID.randomUUID();
        UUID reintegroId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        OffsetDateTime fechaEvento = OffsetDateTime.parse("2027-03-15T00:00:00Z");

        jdbcTemplate.update("""
                INSERT INTO orden (id, cliente_id, cliente_email, evento_id, evento_organizador_id,
                                   evento_nombre, evento_fecha, sector_nombre, evento_tipo, evento_ciudad,
                                   reserva_id, estado, total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ordenId, clienteId, "cliente@example.com", eventoId, organizadorId,
                "Recital", fechaEvento, "Platea A", "SHOW", "Tandil",
                UUID.randomUUID(), "PAGADA", new BigDecimal("10000.00"));
        jdbcTemplate.update("""
                INSERT INTO orden_item (id, orden_id, sector_id, cantidad, precio_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)
                """, itemId, ordenId, sectorId, 2, new BigDecimal("5000.00"), new BigDecimal("10000.00"));
        jdbcTemplate.update("""
                INSERT INTO pago (id, orden_id, estado, monto, medio_pago)
                VALUES (?, ?, ?, ?, ?)
                """, pagoId, ordenId, "REINTEGRO_PENDIENTE", new BigDecimal("10000.00"), "TARJETA");
        insertarEntrada(entradaVigente, ordenId, itemId, clienteId, eventoId,
                sectorId, fechaEvento, "VALIDA");
        insertarEntrada(entradaDevuelta, ordenId, itemId, clienteId, eventoId,
                sectorId, fechaEvento, "DEVUELTA");
        jdbcTemplate.update("""
                INSERT INTO devolucion (id, entrada_id, estado, motivo)
                VALUES (?, ?, ?, ?)
                """, devolucionId, entradaDevuelta, "APROBADA", "No puedo asistir");
        jdbcTemplate.update("""
                INSERT INTO reintegro (id, pago_id, entrada_id, monto, estado)
                VALUES (?, ?, ?, ?, ?)
                """, reintegroId, pagoId, entradaDevuelta, new BigDecimal("5000.00"), "APROBADO");

        when(catalogReportClient.obtenerReporte(eventoId)).thenReturn(new CatalogReport(
                eventoId, "Recital", organizadorId,
                List.of(new CatalogSector(sectorId, "Platea A", "NUMERADO", 10,
                        new BigDecimal("5000.00")))));

        EventReportResponse response = reportService.reporte(eventoId,
                new RequestIdentity(organizadorId, "ORGANIZER"));

        assertThat(response.organizadorId()).isEqualTo(organizadorId);
        assertThat(response.datosDesactualizados()).isFalse();
        assertThat(response.sectores()).hasSize(1);
        SectorReportResponse sector = response.sectores().get(0);
        assertThat(sector.vendidas()).isEqualTo(2);
        assertThat(sector.devueltas()).isEqualTo(1);
        assertThat(sector.netas()).isEqualTo(1);
        assertThat(sector.ocupacion()).isEqualByComparingTo("0.1");
        assertThat(sector.recaudacion()).isEqualByComparingTo("5000.00");
        assertThat(response.totales().vendidas()).isEqualTo(2);
        assertThat(response.totales().netas()).isEqualTo(1);
        assertThat(response.totales().recaudacion()).isEqualByComparingTo("5000.00");
    }

    @Test
    void catalogCaidoUsaCacheYMarcaDatosDesactualizados() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID pagoId = UUID.randomUUID();
        UUID entradaId = UUID.randomUUID();
        OffsetDateTime fechaEvento = OffsetDateTime.parse("2027-03-15T00:00:00Z");

        jdbcTemplate.update("""
                INSERT INTO orden (id, cliente_id, evento_id, evento_organizador_id, evento_nombre,
                                   evento_fecha, sector_nombre, reserva_id, estado, total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ordenId, UUID.randomUUID(), eventoId, organizadorId, "Recital",
                fechaEvento, "Platea A", UUID.randomUUID(), "PAGADA", new BigDecimal("5000.00"));
        jdbcTemplate.update("""
                INSERT INTO orden_item (id, orden_id, sector_id, cantidad, precio_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)
                """, itemId, ordenId, sectorId, 1, new BigDecimal("5000.00"), new BigDecimal("5000.00"));
        jdbcTemplate.update("""
                INSERT INTO pago (id, orden_id, estado, monto, medio_pago)
                VALUES (?, ?, ?, ?, ?)
                """, pagoId, ordenId, "APROBADO", new BigDecimal("5000.00"), "TARJETA");
        insertarEntrada(entradaId, ordenId, itemId, UUID.randomUUID(), eventoId,
                sectorId, fechaEvento, "VALIDA");

        CatalogReport reporteCatalogo = new CatalogReport(eventoId, "Recital", organizadorId,
                List.of(new CatalogSector(sectorId, "Platea A", "NUMERADO", 100,
                        new BigDecimal("5000.00"))));
        when(catalogReportClient.obtenerReporte(eventoId))
                .thenReturn(reporteCatalogo)
                .thenThrow(new org.springframework.web.client.ResourceAccessException("catalog caído"));

        reportService.reporte(eventoId, new RequestIdentity(organizadorId, "ORGANIZER"));
        EventReportResponse response = reportService.reporte(eventoId,
                new RequestIdentity(organizadorId, "ORGANIZER"));

        assertThat(response.datosDesactualizados()).isTrue();
        assertThat(response.sectores().get(0).capacidad()).isEqualTo(100);
        assertThat(response.sectores().get(0).ocupacion()).isEqualByComparingTo("0.01");
    }

    private void insertarEntrada(UUID entradaId, UUID ordenId, UUID itemId, UUID clienteId,
                                 UUID eventoId, UUID sectorId, OffsetDateTime fechaEvento,
                                 String estado) {
        jdbcTemplate.update("""
                INSERT INTO entrada (id, orden_id, orden_item_id, cliente_id, evento_id,
                                     evento_nombre, evento_fecha, sector_id, qr_code, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entradaId, ordenId, itemId, clienteId, eventoId, "Recital", fechaEvento,
                sectorId, UUID.randomUUID(), estado);
    }
}
