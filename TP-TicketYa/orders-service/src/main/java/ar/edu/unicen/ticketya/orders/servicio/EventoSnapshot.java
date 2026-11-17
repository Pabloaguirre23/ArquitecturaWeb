package ar.edu.unicen.ticketya.orders.servicio;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proyección del detalle de catalog que se guarda en `orden` como snapshot.
 * `tipo`, `artistas` y `ciudad` son los campos que exige el payload de
 * `orden.pagada` en `docs/eventos.md`.
 */
public record EventoSnapshot(
        String tipo,
        List<String> artistas,
        String ciudad,
        UUID organizadorId,
        Map<UUID, String> sectores) {
}
