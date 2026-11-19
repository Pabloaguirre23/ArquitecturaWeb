package ar.edu.unicen.ticketya.recommendation.web;

import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasRequest;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/preferences")
@Tag(name = "Preferencias", description = "Preferencias del cliente autenticado")
@SecurityRequirement(name = "X-User-Id")
@SecurityRequirement(name = "X-User-Role")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    @Operation(summary = "Consultar mis preferencias",
            description = "Solo el cliente autenticado puede leer sus propias preferencias. "
                    + "Si nunca las registró, devuelve listas vacías y ciudad nula.")
    @ApiResponse(responseCode = "200", description = "Preferencias del cliente")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT (FORBIDDEN)")
    public PreferenciasResponse obtener(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return preferenceService.obtener(identity.userId());
    }

    @PutMapping
    @Operation(summary = "Registrar o actualizar mis preferencias",
            description = "Upsert idempotente por cliente. Los tipos se validan contra el catálogo "
                    + "de eventos y la ciudad es obligatoria.")
    @ApiResponse(responseCode = "200", description = "Preferencias guardadas")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR o INVALID_EVENT_TYPE)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT o clienteId ajeno (FORBIDDEN)")
    public PreferenciasResponse guardar(
            @Valid @RequestBody PreferenciasRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return preferenceService.guardar(identity.userId(), request);
    }
}
