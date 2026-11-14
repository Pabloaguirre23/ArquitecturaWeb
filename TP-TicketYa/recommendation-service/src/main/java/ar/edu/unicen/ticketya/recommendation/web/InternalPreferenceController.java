package ar.edu.unicen.ticketya.recommendation.web;

import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno para notification-service: consulta las preferencias de un
 * cliente para decidir si le avisa de un artista favorito. No se expone por el
 * gateway y exige X-Internal-Token.
 */
@RestController
@RequestMapping("/internal/preferences")
@Tag(name = "Preferencias internas", description = "Consulta entre servicios")
@SecurityRequirement(name = "X-Internal-Token")
public class InternalPreferenceController {

    private final PreferenceService preferenceService;
    private final InternalToken internalToken;

    public InternalPreferenceController(PreferenceService preferenceService, InternalToken internalToken) {
        this.preferenceService = preferenceService;
        this.internalToken = internalToken;
    }

    @GetMapping("/{clienteId}")
    @Operation(summary = "Preferencias de un cliente (interno)",
            description = "Uso exclusivo de otros servicios; requiere X-Internal-Token.")
    @ApiResponse(responseCode = "200", description = "Preferencias del cliente o vacías si no registró")
    @ApiResponse(responseCode = "401", description = "Token interno ausente o inválido (UNAUTHORIZED)")
    public PreferenciasResponse obtener(
            @PathVariable UUID clienteId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalToken.require(token);
        return preferenceService.obtener(clienteId);
    }
}
