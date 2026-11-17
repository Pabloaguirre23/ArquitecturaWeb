package ar.edu.unicen.ticketya.queue.web;

import ar.edu.unicen.ticketya.queue.servicio.FilaService;
import ar.edu.unicen.ticketya.queue.web.dto.PosicionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
@Tag(name = "Fila virtual", description = "Ingreso y posición en la fila de un evento de alta demanda.")
public class QueueController {

    private final FilaService filaService;

    public QueueController(FilaService filaService) {
        this.filaService = filaService;
    }

    @PostMapping("/events/{eventoId}/join")
    @Operation(summary = "Ingresar a la fila",
            description = """
                    Primer ingreso: 201, posición según el orden de llegada y estado EN_ESPERA. \
                    Repetir el ingreso conserva la posición (ZADD NX) y responde 200. \
                    Un evento sin fila responde 409 QUEUE_NOT_OPEN.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Primer ingreso"),
            @ApiResponse(responseCode = "200", description = "El cliente ya estaba en la fila"),
            @ApiResponse(responseCode = "401", description = "Falta la identidad"),
            @ApiResponse(responseCode = "403", description = "El rol no es CLIENT"),
            @ApiResponse(responseCode = "409", description = "El evento no tiene fila o la fila está cerrada")
    })
    public ResponseEntity<PosicionResponse> ingresar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        RequestIdentity identity = RequestIdentity.require(userId, role);
        identity.requireRole("CLIENT");
        FilaService.Ingreso ingreso = filaService.ingresar(eventoId, identity.userId());
        if (ingreso.nuevo()) {
            return ResponseEntity.created(URI.create("/api/queue/events/" + eventoId + "/position"))
                    .body(ingreso.posicion());
        }
        return ResponseEntity.ok(ingreso.posicion());
    }

    @GetMapping("/events/{eventoId}/position")
    @Operation(summary = "Consultar la posición propia",
            description = """
                    En espera: posición (ZRANK + 1), personas adelante y estimación. \
                    Habilitado por una tanda: estado HABILITADO y el token de compra. \
                    404 si nunca ingresó o si el token ya venció.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posición actual"),
            @ApiResponse(responseCode = "401", description = "Falta la identidad"),
            @ApiResponse(responseCode = "403", description = "El rol no es CLIENT"),
            @ApiResponse(responseCode = "404", description = "El cliente no está en la fila")
    })
    public PosicionResponse posicion(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        RequestIdentity identity = RequestIdentity.require(userId, role);
        identity.requireRole("CLIENT");
        return filaService.consultar(eventoId, identity.userId());
    }
}
