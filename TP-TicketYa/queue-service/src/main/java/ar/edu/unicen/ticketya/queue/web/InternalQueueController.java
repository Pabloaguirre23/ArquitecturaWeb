package ar.edu.unicen.ticketya.queue.web;

import ar.edu.unicen.ticketya.queue.config.QueueProperties;
import ar.edu.unicen.ticketya.queue.servicio.FilaService;
import ar.edu.unicen.ticketya.queue.web.dto.AbrirFilaRequest;
import ar.edu.unicen.ticketya.queue.web.dto.CerrarFilaRequest;
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
@Tag(name = "Fila interna", description = "Apertura y cierre de la fila. Catalog la abre al publicar y la cierra al cancelar o finalizar. Inventory la cierra al agotar.")
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
        exigirToken(token);
        Integer batch = body == null ? null : body.batchSize();
        Integer intervalo = body == null ? null : body.intervaloSegundos();
        FilaService.Apertura apertura = filaService.abrir(eventoId, batch, intervalo);
        if (apertura.creada()) {
            return ResponseEntity.status(201).body(apertura.estado());
        }
        return ResponseEntity.ok(apertura.estado());
    }

    @PostMapping("/{eventoId}/close")
    @Operation(summary = "Cerrar la fila de un evento",
            description = """
                    La llama catalog al cancelar o finalizar, e inventory al agotar. \
                    No admite ingresos nuevos ni emite tokens. Quien estaba en la fila consulta estado CERRADA. \
                    Repetir el cierre no cambia el motivo original.
                    """)
    public FilaEstadoResponse cerrar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody @Valid CerrarFilaRequest body) {
        exigirToken(token);
        return filaService.cerrar(eventoId, body.motivo());
    }

    private void exigirToken(String token) {
        if (token == null || !token.equals(properties.internalToken())) {
            throw ApiException.unauthorized("Falta el encabezado X-Internal-Token");
        }
    }
}
