package ar.edu.unicen.ticketya.queue.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CerrarFilaRequest(
        @NotBlank(message = "motivo es obligatorio")
        @Pattern(regexp = "CANCELADO|FINALIZADO|AGOTADO", message = "motivo debe ser CANCELADO, FINALIZADO o AGOTADO")
        String motivo) {
}
