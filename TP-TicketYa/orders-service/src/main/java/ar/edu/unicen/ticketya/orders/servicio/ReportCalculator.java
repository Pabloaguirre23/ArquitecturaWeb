package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import ar.edu.unicen.ticketya.orders.web.dto.ReportTotalsResponse;
import ar.edu.unicen.ticketya.orders.web.dto.SectorReportResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Cálculo puro del reporte (tarea 6.5): vendidas son las entradas de órdenes
 * pagadas (incluidas las luego devueltas), devueltas las que están DEVUELTA o
 * tienen devolución aprobada, netas = vendidas - devueltas, ocupación =
 * netas / capacidad y recaudación = ítems cobrados menos reintegros aprobados.
 */
@Component
public class ReportCalculator {

    public EventReportResponse calcular(ReportData data) {
        Set<UUID> ordenesPagadas = data.ordenesPagadas().stream()
                .map(Orden::getId)
                .collect(Collectors.toSet());

        Map<UUID, String> nombrePorSector = new LinkedHashMap<>();
        for (CatalogSector sector : data.sectoresCatalogo()) {
            if (sector.sectorId() != null) {
                nombrePorSector.put(sector.sectorId(), sector.nombre());
            }
        }
        // Si catalog no dio el nombre, se completa con el snapshot de la orden.
        for (OrdenItem item : data.items()) {
            nombrePorSector.putIfAbsent(item.getSectorId(),
                    nombreSnapshot(data.ordenesPagadas(), item.getOrdenId()));
        }
        for (Entrada entrada : data.entradas()) {
            nombrePorSector.putIfAbsent(entrada.getSectorId(), null);
        }

        Map<UUID, UUID> sectorDeEntrada = data.entradas().stream()
                .collect(Collectors.toMap(Entrada::getId, Entrada::getSectorId, (a, b) -> a));
        Map<UUID, Integer> capacidadPorSector = data.sectoresCatalogo().stream()
                .filter(s -> s.sectorId() != null)
                .collect(Collectors.toMap(CatalogSector::sectorId,
                        s -> s.capacidad(), (a, b) -> a, LinkedHashMap::new));

        List<SectorReportResponse> sectores = new ArrayList<>();
        long totalVendidas = 0;
        long totalDevueltas = 0;
        for (UUID sectorId : nombrePorSector.keySet()) {
            long vendidas = data.entradas().stream()
                    .filter(e -> sectorId.equals(e.getSectorId())
                            && ordenesPagadas.contains(e.getOrdenId()))
                    .count();
            long devueltas = data.entradas().stream()
                    .filter(e -> sectorId.equals(e.getSectorId())
                            && ordenesPagadas.contains(e.getOrdenId())
                            && (e.getEstado() == EstadoEntrada.DEVUELTA
                                || data.entradaIdsDevueltas().contains(e.getId())))
                    .count();
            long netas = vendidas - devueltas;
            Integer capacidad = capacidadPorSector.get(sectorId);
            BigDecimal ocupacion = ocupacion(netas, capacidad);
            BigDecimal recaudacion = recaudacionSector(data, sectorId, sectorDeEntrada);

            sectores.add(new SectorReportResponse(sectorId, nombrePorSector.get(sectorId),
                    capacidad, vendidas, devueltas, netas, ocupacion, recaudacion));
            totalVendidas += vendidas;
            totalDevueltas += devueltas;
        }

        BigDecimal cobrado = data.items().stream()
                .map(OrdenItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reintegrado = data.reintegrosAprobados().stream()
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recaudacionTotal = cobrado.subtract(reintegrado).setScale(2, RoundingMode.HALF_UP);

        ReportTotalsResponse totales = new ReportTotalsResponse(
                totalVendidas, totalDevueltas, totalVendidas - totalDevueltas, recaudacionTotal);

        return new EventReportResponse(data.eventoId(), data.organizadorId(),
                List.copyOf(sectores), totales, data.datosDesactualizados(), data.generadoEn());
    }

    private BigDecimal ocupacion(long netas, Integer capacidad) {
        if (capacidad == null || capacidad <= 0) {
            return null;
        }
        return BigDecimal.valueOf(netas)
                .divide(BigDecimal.valueOf(capacidad), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private BigDecimal recaudacionSector(ReportData data, UUID sectorId,
                                         Map<UUID, UUID> sectorDeEntrada) {
        BigDecimal cobrado = data.items().stream()
                .filter(i -> sectorId.equals(i.getSectorId()))
                .map(OrdenItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reintegrado = data.reintegrosAprobados().stream()
                .filter(r -> r.getEntradaId() != null
                        && sectorId.equals(sectorDeEntrada.get(r.getEntradaId())))
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cobrado.subtract(reintegrado).setScale(2, RoundingMode.HALF_UP);
    }

    private String nombreSnapshot(List<Orden> ordenes, UUID ordenId) {
        return ordenes.stream()
                .filter(o -> o.getId().equals(ordenId))
                .map(Orden::getSectorNombre)
                .findFirst()
                .orElse(null);
    }
}
