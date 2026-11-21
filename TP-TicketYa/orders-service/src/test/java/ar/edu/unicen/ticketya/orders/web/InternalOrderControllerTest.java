package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.config.InternalTokenConfig;
import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CancelacionEventoResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderController.class)
@Import(InternalTokenConfig.class)
@TestPropertySource(properties = "orders.internal-token=test-internal")
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void cancelarEventoDevuelveCantidadDeEntradas() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(orderService.cancelarEvento(eventoId))
                .thenReturn(new CancelacionEventoResponse(eventoId, 3, 2));

        mockMvc.perform(post("/internal/events/{eventoId}/cancel", eventoId)
                        .header("X-Internal-Token", "test-internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.entradasAfectadas").value(3))
                .andExpect(jsonPath("$.reintegrosSolicitados").value(2));
    }

    @Test
    void cancelarEventoSinEntradasDevuelveCero() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(orderService.cancelarEvento(eventoId))
                .thenReturn(new CancelacionEventoResponse(eventoId, 0, 0));

        mockMvc.perform(post("/internal/events/{eventoId}/cancel", eventoId)
                        .header("X-Internal-Token", "test-internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.entradasAfectadas").value(0))
                .andExpect(jsonPath("$.reintegrosSolicitados").value(0));
    }

    @Test
    void sinTokenInternoDevuelve401() throws Exception {
        mockMvc.perform(post("/internal/events/{eventoId}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
