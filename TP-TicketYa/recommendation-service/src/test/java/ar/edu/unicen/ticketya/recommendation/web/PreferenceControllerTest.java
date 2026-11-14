package ar.edu.unicen.ticketya.recommendation.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasRequest;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PreferenceController.class)
class PreferenceControllerTest {

    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String CLIENTE_TEXTO = CLIENTE.toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    void consultarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(get("/api/recommendations/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void consultarConRolOrganizadorDevuelve403() throws Exception {
        mockMvc.perform(get("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void consultarDevuelveLasPreferenciasPropias() throws Exception {
        when(preferenceService.obtener(CLIENTE))
                .thenReturn(new PreferenciasResponse(CLIENTE_TEXTO, List.of("RECITAL"),
                        List.of("Los Piojos"), "Tandil", Instant.parse("2026-11-14T12:00:00Z")));

        mockMvc.perform(get("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(CLIENTE_TEXTO))
                .andExpect(jsonPath("$.tipos[0]").value("RECITAL"))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));
    }

    @Test
    void consultarSinPreferenciasDevuelveListasVacias() throws Exception {
        when(preferenceService.obtener(CLIENTE))
                .thenReturn(new PreferenciasResponse(CLIENTE_TEXTO, List.of(), List.of(), null, null));

        mockMvc.perform(get("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos").isEmpty())
                .andExpect(jsonPath("$.artistas").isEmpty())
                .andExpect(jsonPath("$.ciudad").doesNotExist());
    }

    @Test
    void guardarPreferenciasValidasDevuelve200() throws Exception {
        when(preferenceService.guardar(eq(CLIENTE), any(PreferenciasRequest.class)))
                .thenReturn(new PreferenciasResponse(CLIENTE_TEXTO, List.of("FESTIVAL"),
                        List.of("Los Piojos"), "Tandil", Instant.parse("2026-11-14T12:00:00Z")));

        mockMvc.perform(put("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipos":["FESTIVAL"],"artistas":["Los Piojos"],"ciudad":"Tandil"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos[0]").value("FESTIVAL"))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));

        verify(preferenceService).guardar(eq(CLIENTE), any(PreferenciasRequest.class));
    }

    @Test
    void guardarSinCiudadDevuelve400ConElCampo() throws Exception {
        mockMvc.perform(put("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipos":["FESTIVAL"],"artistas":[],"ciudad":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("ciudad"));
    }

    @Test
    void guardarTipoDesconocidoDevuelve400() throws Exception {
        when(preferenceService.guardar(eq(CLIENTE), any(PreferenciasRequest.class)))
                .thenThrow(ApiException.badRequest("INVALID_EVENT_TYPE",
                        "El tipo de evento OPERA no es conocido"));

        mockMvc.perform(put("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipos":["OPERA"],"artistas":[],"ciudad":"Tandil"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_TYPE"));
    }

    @Test
    void guardarConClienteIdAjenoDevuelve403() throws Exception {
        when(preferenceService.guardar(eq(CLIENTE), any(PreferenciasRequest.class)))
                .thenThrow(ApiException.forbidden("NOT_PREFERENCE_OWNER",
                        "Las preferencias pertenecen a otro cliente"));

        mockMvc.perform(put("/api/recommendations/preferences")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipos":["FESTIVAL"],"ciudad":"Tandil",
                                 "clienteId":"99999999-9999-4999-8999-999999999999"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PREFERENCE_OWNER"));
    }
}
