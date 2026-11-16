package ar.edu.unicen.ticketya.queue.web.dto;

import java.util.UUID;

public record FilaEstadoResponse(
        UUID eventoId,
        String estado,
        int batchSize,
        int intervaloSegundos) {
}
