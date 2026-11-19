package ar.edu.unicen.ticketya.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record EventoSimilarItem(
        String eventoId,
        String nombre,
        String tipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        long compradoresEnComun) {
}
