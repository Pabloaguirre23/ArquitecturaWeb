package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.orders.eventos.CorrelationContext;
import ar.edu.unicen.ticketya.orders.eventos.EventEnvelope;
import ar.edu.unicen.ticketya.orders.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Escribe el evento en `outbox_event` dentro de la transacción que cambia el
 * estado de negocio (tarea 6.3). El `eventId` es determinísta por
 * (tipo, agregado), así un reintento o una compensación repetida reutiliza el
 * mismo identificador y el consumidor no duplica el efecto.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void registrar(String type, UUID aggregateId, Map<String, Object> payload) {
        registrar(type, aggregateId, CorrelationContext.id(), payload, OffsetDateTime.now(clock));
    }

    public void registrar(String type, UUID aggregateId, String correlationId,
                          Map<String, Object> payload, OffsetDateTime occurredAt) {
        UUID eventId = eventId(type, aggregateId);
        if (repository.existsByEventId(eventId)) {
            log.debug("El evento {} del agregado {} ya está en el outbox", type, aggregateId);
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        EventEnvelope envelope = EventEnvelope.of(eventId, type, occurredAt, correlationId, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope.asMap());
            repository.save(new OutboxEvent(UUID.randomUUID(), eventId, type, aggregateId,
                    ahora, correlationId, EventEnvelope.VERSION, json, ahora));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de outbox " + type, ex);
        } catch (DataIntegrityViolationException ex) {
            // Otro hilo registró el mismo hecho: el eventId único evita el duplicado.
            log.debug("Evento duplicado en el outbox: {}", eventId);
        }
    }

    /** Identificador estable del hecho de dominio; se conserva en cada reintento. */
    public static UUID eventId(String type, UUID aggregateId) {
        return UUID.nameUUIDFromBytes((type + ":" + aggregateId).getBytes(StandardCharsets.UTF_8));
    }
}
