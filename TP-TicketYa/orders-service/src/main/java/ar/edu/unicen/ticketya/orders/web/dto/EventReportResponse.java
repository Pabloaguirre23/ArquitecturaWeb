package ar.edu.unicen.ticketya.orders.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Reporte de ventas y ocupación de un evento")
public record EventReportResponse(
        UUID eventoId,
        UUID organizadorId,
        List<SectorReportResponse> sectores,
        ReportTotalsResponse totales,
        @Schema(description = "true cuando catalog no respondió y se usaron datos en cache o del snapshot")
        boolean datosDesactualizados,
        OffsetDateTime generadoEn) {
}
