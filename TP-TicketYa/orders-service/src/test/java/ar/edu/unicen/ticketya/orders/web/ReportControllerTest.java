package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.servicio.ReportService;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import ar.edu.unicen.ticketya.orders.web.dto.ReportTotalsResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    private static final String ORGANIZADOR = "22222222-2222-4222-8222-222222222222";
    private static final String OTRO_ORGANIZADOR = "22222222-2222-4222-8222-222222222299";
    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void sinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(get("/api/orders/reports/events/{eventoId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void clienteDevuelve403() throws Exception {
        mockMvc.perform(perform(UUID.randomUUID(), CLIENTE, "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizadorDuenoDevuelve200() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(reportService.reporte(eq(eventoId), any(RequestIdentity.class)))
                .thenReturn(reporte(eventoId, true));

        mockMvc.perform(perform(eventoId, ORGANIZADOR, "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.organizadorId").value(ORGANIZADOR))
                .andExpect(jsonPath("$.totales.vendidas").value(2))
                .andExpect(jsonPath("$.datosDesactualizados").value(true));
    }

    @Test
    void adminDevuelve200() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(reportService.reporte(eq(eventoId), any(RequestIdentity.class)))
                .thenReturn(reporte(eventoId, false));

        mockMvc.perform(perform(eventoId, ADMIN, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.netas").value(1));
    }

    @Test
    void otroOrganizadorDevuelve403() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(reportService.reporte(eq(eventoId), any(RequestIdentity.class)))
                .thenThrow(ApiException.forbidden("NOT_EVENT_OWNER", "El evento pertenece a otro organizador"));

        mockMvc.perform(perform(eventoId, OTRO_ORGANIZADOR, "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_EVENT_OWNER"));
    }

    @Test
    void eventoInexistenteDevuelve404() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(reportService.reporte(eq(eventoId), any(RequestIdentity.class)))
                .thenThrow(ApiException.notFound("EVENT_NOT_FOUND", "El evento no existe"));

        mockMvc.perform(perform(eventoId, ADMIN, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void eventoSinVentasDevuelveCeros() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(reportService.reporte(eq(eventoId), any(RequestIdentity.class)))
                .thenReturn(new EventReportResponse(eventoId, UUID.fromString(ORGANIZADOR),
                        List.of(), new ReportTotalsResponse(0, 0, 0, BigDecimal.ZERO),
                        false, OffsetDateTime.now()));

        mockMvc.perform(perform(eventoId, ORGANIZADOR, "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectores").isEmpty())
                .andExpect(jsonPath("$.totales.vendidas").value(0))
                .andExpect(jsonPath("$.totales.recaudacion").value(0));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            perform(UUID eventoId, String userId, String role) {
        return get("/api/orders/reports/events/{eventoId}", eventoId)
                .header("X-User-Id", userId)
                .header("X-User-Role", role);
    }

    private static EventReportResponse reporte(UUID eventoId, boolean desactualizado) {
        return new EventReportResponse(eventoId, UUID.fromString(ORGANIZADOR), List.of(),
                new ReportTotalsResponse(2, 1, 1, new BigDecimal("5000.00")),
                desactualizado, OffsetDateTime.now());
    }
}
