package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Datos ya recolectados por ReportService para que el cálculo sea puro y testeable. */
public record ReportData(
        UUID eventoId,
        UUID organizadorId,
        List<Orden> ordenesPagadas,
        List<OrdenItem> items,
        List<Entrada> entradas,
        Set<UUID> entradaIdsDevueltas,
        List<Reintegro> reintegrosAprobados,
        List<CatalogSector> sectoresCatalogo,
        boolean datosDesactualizados,
        OffsetDateTime generadoEn) {
}
