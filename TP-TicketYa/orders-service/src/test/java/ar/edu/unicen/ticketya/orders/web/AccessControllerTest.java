package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.servicio.AccessService;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationRequest;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccessController.class)
class AccessControllerTest {

    private static final String STAFF = "55555555-5555-4555-8555-555555555555";
    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessService accessService;

    @Test
    void validarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validarConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validarConStaffDevuelve200() throws Exception {
        UUID qr = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        UUID entrada = UUID.randomUUID();
        UUID orden = UUID.randomUUID();
        UUID sector = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.parse("2026-11-06T12:00:00-03:00");
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenReturn(new AccessValidationResponse(entrada, orden, evento, sector,
                        "UTILIZADA", ahora, UUID.fromString(STAFF)));

        mockMvc.perform(performBody(qr, evento)
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(entrada.toString()))
                .andExpect(jsonPath("$.ordenId").value(orden.toString()))
                .andExpect(jsonPath("$.estado").value("UTILIZADA"))
                .andExpect(jsonPath("$.usedBy").value(STAFF));
    }

    @Test
    void validarConAdminDevuelve200() throws Exception {
        UUID entrada = UUID.randomUUID();
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenReturn(new AccessValidationResponse(entrada, UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), "UTILIZADA",
                        OffsetDateTime.now(), UUID.fromString(ADMIN)));

        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(entrada.toString()));
    }

    @Test
    void validarEntradaInexistenteDevuelve404() throws Exception {
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenThrow(ApiException.notFound("TICKET_NOT_FOUND", "No existe la entrada"));

        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void validarEntradaYaUsadaDevuelve409() throws Exception {
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenThrow(ApiException.conflict("TICKET_ALREADY_USED", "La entrada ya fue utilizada"));

        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_USED"));
    }

    @Test
    void validarOtroEventoDevuelve409() throws Exception {
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenThrow(ApiException.conflict("TICKET_WRONG_EVENT", "La entrada pertenece a otro evento"));

        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_WRONG_EVENT"));
    }

    @Test
    void validarEntradaNoVigenteDevuelve409() throws Exception {
        when(accessService.validar(any(AccessValidationRequest.class), any(RequestIdentity.class)))
                .thenThrow(ApiException.conflict("TICKET_NOT_VALID", "La entrada no está vigente"));

        mockMvc.perform(performBody(UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_VALID"));
    }

    @Test
    void validarSinQrDevuelve400() throws Exception {
        mockMvc.perform(post("/api/orders/access/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"" + UUID.randomUUID() + "\"}")
                        .header("X-User-Id", STAFF)
                        .header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            performBody(UUID qrCode, UUID eventoId) {
        return post("/api/orders/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrCode\":\"" + qrCode + "\",\"eventoId\":\"" + eventoId + "\"}");
    }
}
