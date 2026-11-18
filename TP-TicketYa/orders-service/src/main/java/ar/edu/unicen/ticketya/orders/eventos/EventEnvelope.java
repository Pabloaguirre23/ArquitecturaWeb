package ar.edu.unicen.ticketya.orders.eventos;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de eventos de dominio (tarea 1.5). El JSON publicado por el outbox
 * respeta exactamente estos nombres de campo y el orden del contrato de
 * {@code docs/eventos.md}.
 */
public record EventEnvelope(
        String eventId,
        String type,
        String occurredAt,
        String correlationId,
        int version,
        Map<String, Object> payload) {

    public static final int VERSION = 1;

    public static EventEnvelope of(UUID eventId, String type, OffsetDateTime occurredAt,
                                   String correlationId, Map<String, Object> payload) {
        return new EventEnvelope(
                eventId.toString(),
                type,
                DateTimeFormatter.ISO_INSTANT.format(occurredAt.withOffsetSameInstant(ZoneOffset.UTC)),
                correlationId,
                VERSION,
                payload);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("eventId", eventId);
        mapa.put("type", type);
        mapa.put("occurredAt", occurredAt);
        mapa.put("correlationId", correlationId);
        mapa.put("version", version);
        mapa.put("payload", payload);
        return mapa;
    }
}
