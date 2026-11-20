package ar.edu.unicen.ticketya.orders.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Totales del evento")
public record ReportTotalsResponse(
        long vendidas,
        long devueltas,
        long netas,
        BigDecimal recaudacion) {
}
