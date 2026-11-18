package ar.edu.unicen.ticketya.queue.web.dto;

import jakarta.validation.constraints.Min;

public record AbrirFilaRequest(
        @Min(value = 1, message = "batchSize debe ser al menos 1") Integer batchSize,
        @Min(value = 1, message = "intervaloSegundos debe ser al menos 1") Integer intervaloSegundos) {
}
