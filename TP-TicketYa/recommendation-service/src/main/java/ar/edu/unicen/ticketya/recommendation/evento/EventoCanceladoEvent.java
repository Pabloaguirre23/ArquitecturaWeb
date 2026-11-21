package ar.edu.unicen.ticketya.recommendation.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Sobre de los eventos de catálogo que llegan a recommendation.events.
 * Solo se consume `evento.cancelado` para que los snapshots de
 * `eventos_vistos` no sigan recomendando eventos dados de baja.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoCanceladoEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        Integer version,
        JsonNode payload) {

    public static final String TIPO = "evento.cancelado";

    public String eventoId() {
        return payload == null ? null : payload.path("eventoId").asText(null);
    }
}
