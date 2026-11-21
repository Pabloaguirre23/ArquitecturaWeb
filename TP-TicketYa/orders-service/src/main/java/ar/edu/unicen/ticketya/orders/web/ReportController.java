package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.ReportService;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/reports")
@Tag(name = "Reportes", description = "Reporte de ventas y ocupación para organizadores")
public class ReportController {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("ORGANIZER", "ADMIN");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/events/{eventoId}")
    @Operation(summary = "Reporte de un evento",
            description = "Solo el organizador dueño o un ADMIN. Usa capacidad y precios de "
                    + "catalog con cache de 60 s para degradar si no responde.")
    @ApiResponse(responseCode = "200", description = "Reporte del evento")
    @ApiResponse(responseCode = "403", description = "Rol no permitido o evento ajeno")
    @ApiResponse(responseCode = "404", description = "El evento no existe (EVENT_NOT_FOUND)")
    public EventReportResponse reporte(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        if (!ROLES_PERMITIDOS.contains(identity.role())) {
            throw ApiException.forbidden("FORBIDDEN",
                    "El rol " + identity.role() + " no puede consultar reportes");
        }
        return reportService.reporte(eventoId, identity);
    }
}
