package ar.edu.unicen.ticketya.recommendation.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalPreferenceController.class)
@Import(InternalToken.class)
@TestPropertySource(properties = "recommendation.internal-token=test-internal")
class InternalPreferenceControllerTest {

    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    void sinTokenInternoDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/preferences/{clienteId}", CLIENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void conTokenIncorrectoDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/preferences/{clienteId}", CLIENTE)
                        .header("X-Internal-Token", "otro-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void conTokenDevuelveLasPreferencias() throws Exception {
        when(preferenceService.obtener(UUID.fromString(CLIENTE)))
                .thenReturn(new PreferenciasResponse(CLIENTE, List.of("RECITAL"),
                        List.of("Los Piojos"), "Tandil", null));

        mockMvc.perform(get("/internal/preferences/{clienteId}", CLIENTE)
                        .header("X-Internal-Token", "test-internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(CLIENTE))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));
    }
}
