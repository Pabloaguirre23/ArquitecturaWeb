package ar.edu.unicen.ticketya.orders.servicio;

import java.util.List;
import java.util.UUID;

/** Datos internos del evento que orders necesita para el reporte (6.5). */
public record CatalogReport(
        UUID eventoId,
        String nombre,
        UUID organizadorId,
        List<CatalogSector> sectores) {
}
