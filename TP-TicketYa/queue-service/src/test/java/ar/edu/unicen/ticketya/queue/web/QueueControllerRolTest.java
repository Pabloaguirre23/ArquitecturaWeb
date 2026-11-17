package ar.edu.unicen.ticketya.queue.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.queue.servicio.FilaService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QueueController.class)
class QueueControllerRolTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FilaService filaService;

    @Test
    void unOrganizadorNoEntraNiConsulta() throws Exception {
        mockMvc.perform(post("/api/queue/events/" + EVENTO + "/join")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/queue/events/" + EVENTO + "/position")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(filaService);
    }
}
