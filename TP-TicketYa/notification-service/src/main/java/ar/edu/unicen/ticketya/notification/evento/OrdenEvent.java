package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Sobre de los eventos de órdenes que llegan a notification.order
 * (docs/eventos.md §2 y §3). La cola consume orden.pagada, orden.reintegrada
 * y devolucion.aprobada con payloads distintos, así que el payload se
 * deserializa como JsonNode y el listener lo convierte al tipo específico
 * según {@code type}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        Integer version,
        JsonNode payload) {

    public static final String ORDEN_PAGADA = "orden.pagada";
    public static final String ORDEN_REINTEGRADA = "orden.reintegrada";
    public static final String DEVOLUCION_APROBADA = "devolucion.aprobada";
}
