package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.AccessService;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationRequest;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/access")
@Tag(name = "Control de acceso", description = "Validación de QR por personal de acceso")
public class AccessController {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("ACCESS_STAFF", "ADMIN");

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @PostMapping("/validate")
    @Operation(summary = "Validar el QR de una entrada",
            description = "Consume atómicamente la entrada si está vigente y corresponde al evento. "
                    + "Requiere ACCESS_STAFF o ADMIN.")
    @ApiResponse(responseCode = "200", description = "Entrada validada y marcada como UTILIZADA")
    @ApiResponse(responseCode = "403", description = "El rol no puede validar entradas")
    @ApiResponse(responseCode = "404", description = "No existe la entrada (TICKET_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Entrada ya usada, de otro evento o no vigente")
    public AccessValidationResponse validar(
            @Valid @RequestBody AccessValidationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        if (!ROLES_PERMITIDOS.contains(identity.role())) {
            throw ApiException.forbidden("FORBIDDEN",
                    "El rol " + identity.role() + " no puede validar entradas");
        }
        return accessService.validar(request, identity);
    }
}
