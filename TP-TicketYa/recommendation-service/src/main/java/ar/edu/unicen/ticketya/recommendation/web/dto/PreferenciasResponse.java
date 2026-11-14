package ar.edu.unicen.ticketya.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record PreferenciasResponse(
        String clienteId,
        List<String> tipos,
        List<String> artistas,
        String ciudad,
        Instant actualizadoEn) {
}
