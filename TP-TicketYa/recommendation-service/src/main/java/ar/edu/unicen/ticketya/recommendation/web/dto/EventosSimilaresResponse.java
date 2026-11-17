package ar.edu.unicen.ticketya.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record EventosSimilaresResponse(
        String eventoId,
        Instant generadoEn,
        List<EventoSimilarItem> similares) {
}
