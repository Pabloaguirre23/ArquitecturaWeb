package ar.edu.unicen.ticketya.inventory.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.inventory.config.InternalTokenConfig;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.servicio.ConfirmacionService;
import ar.edu.unicen.ticketya.inventory.servicio.EstadoEventoService;
import ar.edu.unicen.ticketya.inventory.servicio.InventarioService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalInventoryController.class)
@Import(InternalTokenConfig.class)
@TestPropertySource(properties = "inventory.internal-token=ticketya-internal-dev")
class InternalInventoryTokenTest {

    private static final UUID RESERVA = UUID.fromString("b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventarioService inventarioService;
    @MockitoBean
    private ReservaService reservaService;
    @MockitoBean
    private ConfirmacionService confirmacionService;
    @MockitoBean
    private EstadoEventoService estadoEventoService;

    @Test
    void sinTokenInternoDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/reservations/" + RESERVA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(reservaService);
    }

    @Test
    void unTokenIncorrectoDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/reservations/" + RESERVA)
                        .header("X-Internal-Token", "otro-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(reservaService);
    }

    @Test
    void conTokenInternoLlegaAlServicio() throws Exception {
        org.mockito.Mockito.when(reservaService.obtenerInterno(RESERVA)).thenReturn(new ReservationResponse(
                RESERVA, UUID.randomUUID(), "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                UUID.randomUUID(), EstadoReserva.VIGENTE, OffsetDateTime.parse("2026-11-03T12:10:00-03:00"),
                List.of(), new BigDecimal("0")));

        mockMvc.perform(get("/internal/reservations/" + RESERVA)
                        .header("X-Internal-Token", "ticketya-internal-dev"))
                .andExpect(status().isOk());

        verify(reservaService).obtenerInterno(RESERVA);
    }
}
