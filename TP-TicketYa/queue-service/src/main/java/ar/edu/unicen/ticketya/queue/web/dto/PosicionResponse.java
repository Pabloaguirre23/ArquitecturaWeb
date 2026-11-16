package ar.edu.unicen.ticketya.queue.web.dto;

import java.util.UUID;

public record PosicionResponse(
        UUID eventoId,
        int posicion,
        int adelante,
        String estado,
        int estimacionSegundos) {
}
