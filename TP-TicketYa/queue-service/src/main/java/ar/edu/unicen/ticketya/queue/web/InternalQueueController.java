package ar.edu.unicen.ticketya.queue.web;

import ar.edu.unicen.ticketya.queue.config.QueueProperties;
import ar.edu.unicen.ticketya.queue.servicio.FilaService;
import ar.edu.unicen.ticketya.queue.web.dto.AbrirFilaRequest;
import ar.edu.unicen.ticketya.queue.web.dto.FilaEstadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Fila interna", description = "Apertura de la fila. La llama catalog al publicar un evento de alta demanda.")
public class InternalQueueController {

    private final FilaService filaService;
    private final QueueProperties properties;

    public InternalQueueController(FilaService filaService, QueueProperties properties) {
        this.filaService = filaService;
        this.properties = properties;
    }

    @PostMapping("/{eventoId}")
    @Operation(summary = "Abrir la fila de un evento",
            description = "Crea el hash de estado si no existe. Repetir la llamada no pisa el tamaño de tanda ni el intervalo.")
    public ResponseEntity<FilaEstadoResponse> abrir(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) @Valid AbrirFilaRequest body) {
        if (token == null || !token.equals(properties.internalToken())) {
            throw ApiException.unauthorized("Falta el encabezado X-Internal-Token");
        }
        Integer batch = body == null ? null : body.batchSize();
        Integer intervalo = body == null ? null : body.intervaloSegundos();
        FilaService.Apertura apertura = filaService.abrir(eventoId, batch, intervalo);
        if (apertura.creada()) {
            return ResponseEntity.status(201).body(apertura.estado());
        }
        return ResponseEntity.ok(apertura.estado());
    }
}
