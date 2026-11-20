package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import ar.edu.unicen.ticketya.orders.web.dto.SectorReportResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cálculo puro del reporte (escenarios de la spec 6.5). */
class ReportCalculationTest {

    private final ReportCalculator calculator = new ReportCalculator();

    @Test
    void sectorConCienLugaresCuarentaPagadasYCincoDevueltas() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        UUID pagoId = UUID.randomUUID();

        Orden orden = orden(ordenId, eventoId, organizadorId, sectorId);
        OrdenItem item = new OrdenItem(ordenId, sectorId, null, 40,
                new BigDecimal("15000.00"), new BigDecimal("600000.00"));
        item.setId(UUID.randomUUID());

        List<Entrada> entradas = new ArrayList<>();
        List<Reintegro> reintegros = new ArrayList<>();
        Set<UUID> devueltas = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            Entrada entrada = entrada(ordenId, eventoId, sectorId);
            if (i < 5) {
                entrada.setEstado(EstadoEntrada.DEVUELTA);
                devueltas.add(entrada.getId());
                reintegros.add(new Reintegro(pagoId, entrada.getId(),
                        new BigDecimal("15000.00"), EstadoReintegro.APROBADO));
            }
            entradas.add(entrada);
        }

        EventReportResponse response = calculator.calcular(new ReportData(
                eventoId, organizadorId, List.of(orden), List.of(item), entradas, devueltas,
                reintegros, List.of(new CatalogSector(sectorId, "Platea A", "NUMERADO",
                        100, new BigDecimal("15000.00"))), false, OffsetDateTime.now()));

        assertThat(response.sectores()).hasSize(1);
        SectorReportResponse sector = response.sectores().get(0);
        assertThat(sector.sectorNombre()).isEqualTo("Platea A");
        assertThat(sector.capacidad()).isEqualTo(100);
        assertThat(sector.vendidas()).isEqualTo(40);
        assertThat(sector.devueltas()).isEqualTo(5);
        assertThat(sector.netas()).isEqualTo(35);
        assertThat(sector.ocupacion()).isEqualByComparingTo("0.35");
        assertThat(sector.recaudacion()).isEqualByComparingTo("525000.00");
        assertThat(response.totales().vendidas()).isEqualTo(40);
        assertThat(response.totales().devueltas()).isEqualTo(5);
        assertThat(response.totales().netas()).isEqualTo(35);
        assertThat(response.totales().recaudacion()).isEqualByComparingTo("525000.00");
        assertThat(response.datosDesactualizados()).isFalse();
    }

    @Test
    void zonaNoNumeradaUsaLaCapacidadDelSector() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();

        Orden orden = orden(ordenId, eventoId, organizadorId, sectorId);
        OrdenItem item = new OrdenItem(ordenId, sectorId, null, 300,
                new BigDecimal("10000.00"), new BigDecimal("3000000.00"));
        item.setId(UUID.randomUUID());
        List<Entrada> entradas = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            entradas.add(entrada(ordenId, eventoId, sectorId));
        }

        EventReportResponse response = calculator.calcular(new ReportData(
                eventoId, organizadorId, List.of(orden), List.of(item), entradas,
                Set.of(), List.of(), List.of(new CatalogSector(sectorId, "Campo", "NO_NUMERADO",
                        1000, new BigDecimal("10000.00"))), false, OffsetDateTime.now()));

        SectorReportResponse sector = response.sectores().get(0);
        assertThat(sector.vendidas()).isEqualTo(300);
        assertThat(sector.devueltas()).isZero();
        assertThat(sector.netas()).isEqualTo(300);
        assertThat(sector.ocupacion()).isEqualByComparingTo("0.3");
        assertThat(sector.recaudacion()).isEqualByComparingTo("3000000.00");
    }

    @Test
    void sinCatalogYCapacidadDesconocidaDejaOcupacionNula() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();

        Orden orden = orden(ordenId, eventoId, organizadorId, sectorId);
        OrdenItem item = new OrdenItem(ordenId, sectorId, null, 2,
                new BigDecimal("5000.00"), new BigDecimal("10000.00"));
        item.setId(UUID.randomUUID());

        EventReportResponse response = calculator.calcular(new ReportData(
                eventoId, organizadorId, List.of(orden), List.of(item),
                List.of(entrada(ordenId, eventoId, sectorId), entrada(ordenId, eventoId, sectorId)),
                Set.of(), List.of(), List.of(), true, OffsetDateTime.now()));

        assertThat(response.datosDesactualizados()).isTrue();
        SectorReportResponse sector = response.sectores().get(0);
        assertThat(sector.sectorNombre()).isEqualTo("Platea A");
        assertThat(sector.capacidad()).isNull();
        assertThat(sector.ocupacion()).isNull();
        assertThat(sector.vendidas()).isEqualTo(2);
        assertThat(sector.recaudacion()).isEqualByComparingTo("10000.00");
    }

    @Test
    void eventoSinVentasDevuelveCerosYOcupacionCero() {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();

        EventReportResponse response = calculator.calcular(new ReportData(
                eventoId, organizadorId, List.of(), List.of(), List.of(), Set.of(), List.of(),
                List.of(new CatalogSector(sectorId, "Platea A", "NUMERADO", 100,
                        new BigDecimal("15000.00"))), false, OffsetDateTime.now()));

        SectorReportResponse sector = response.sectores().get(0);
        assertThat(sector.vendidas()).isZero();
        assertThat(sector.devueltas()).isZero();
        assertThat(sector.netas()).isZero();
        assertThat(sector.ocupacion()).isEqualByComparingTo("0");
        assertThat(response.totales().recaudacion()).isEqualByComparingTo("0.00");
    }

    private static Orden orden(UUID ordenId, UUID eventoId, UUID organizadorId, UUID sectorId) {
        Orden orden = new Orden();
        orden.setId(ordenId);
        orden.setClienteId(UUID.randomUUID());
        orden.setEventoId(eventoId);
        orden.setEventoOrganizadorId(organizadorId);
        orden.setEventoNombre("Recital");
        orden.setEventoFecha(OffsetDateTime.parse("2027-03-15T00:00:00Z"));
        orden.setSectorNombre("Platea A");
        orden.setReservaId(UUID.randomUUID());
        orden.setEstado(EstadoOrden.PAGADA);
        orden.setTotal(new BigDecimal("600000.00"));
        // El nombre del sector del snapshot se busca por ordenId en el calculador.
        return orden;
    }

    private static Entrada entrada(UUID ordenId, UUID eventoId, UUID sectorId) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setOrdenId(ordenId);
        entrada.setOrdenItemId(UUID.randomUUID());
        entrada.setClienteId(UUID.randomUUID());
        entrada.setEventoId(eventoId);
        entrada.setEventoNombre("Recital");
        entrada.setEventoFecha(OffsetDateTime.parse("2027-03-15T00:00:00Z"));
        entrada.setSectorId(sectorId);
        entrada.setQrCode(UUID.randomUUID());
        entrada.setEstado(EstadoEntrada.VALIDA);
        return entrada;
    }
}
