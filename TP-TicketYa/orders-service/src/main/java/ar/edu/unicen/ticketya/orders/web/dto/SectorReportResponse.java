package ar.edu.unicen.ticketya.orders.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Métricas de un sector del evento")
public record SectorReportResponse(
        UUID sectorId,
        String sectorNombre,
        Integer capacidad,
        long vendidas,
        long devueltas,
        long netas,
        @Schema(description = "Ocupación (netas / capacidad); null si no hay capacidad conocida")
        BigDecimal ocupacion,
        BigDecimal recaudacion) {
}
