package ar.edu.unicen.ticketya.orders.servicio;

import java.math.BigDecimal;
import java.util.UUID;

/** Capacidad, precio y nombre vigentes de un sector según catalog. */
public record CatalogSector(
        UUID sectorId,
        String nombre,
        String tipo,
        Integer capacidad,
        BigDecimal precio) {
}
