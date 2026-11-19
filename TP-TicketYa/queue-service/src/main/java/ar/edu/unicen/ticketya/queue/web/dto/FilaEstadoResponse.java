package ar.edu.unicen.ticketya.queue.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilaEstadoResponse(
        UUID eventoId,
        String estado,
        int batchSize,
        int intervaloSegundos,
        String motivo) {
}
