package ar.edu.unicen.ticketya.queue.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PosicionResponse(
        UUID eventoId,
        int posicion,
        int adelante,
        String estado,
        int estimacionSegundos,
        String token) {
}
