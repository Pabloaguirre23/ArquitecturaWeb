package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOutbox;
import ar.edu.unicen.ticketya.orders.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.orders.repositorio.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica el outbox con al menos una entrega (tarea 6.3). Toma pendientes con
 * `FOR UPDATE SKIP LOCKED`, envía el sobre original como JSON y marca PUBLICADO.
 * Si RabbitMQ falla conserva el mismo `eventId` y reprograma con backoff: el
 * evento no se pierde y el consumidor lo reconoce aunque haya una reentrega.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final String exchange;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseBackoffSeconds;
    private final long maxBackoffSeconds;

    public OutboxPublisher(OutboxEventRepository repository,
                           RabbitTemplate rabbitTemplate,
                           Clock clock,
                           @Value("${orders.outbox.exchange:ticketya.events}") String exchange,
                           @Value("${orders.outbox.batch-size:50}") int batchSize,
                           @Value("${orders.outbox.max-attempts:10}") int maxAttempts,
                           @Value("${orders.outbox.base-backoff-seconds:5}") long baseBackoffSeconds,
                           @Value("${orders.outbox.max-backoff-seconds:300}") long maxBackoffSeconds) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.exchange = exchange;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    @Scheduled(fixedDelayString = "${orders.outbox.publish-ms:2000}")
    @Transactional
    public void publishPending() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        List<OutboxEvent> eventos = repository.lockPending(ahora, batchSize);
        for (OutboxEvent evento : eventos) {
            try {
                rabbitTemplate.send(exchange, evento.getType(), mensaje(evento));
                evento.setStatus(EstadoOutbox.PUBLISHED);
                evento.setPublishedAt(ahora);
                evento.setLastError(null);
            } catch (RuntimeException ex) {
                int intentos = evento.getAttempts() + 1;
                evento.setAttempts(intentos);
                evento.setLastError(truncar(ex.getMessage()));
                evento.setNextAttemptAt(ahora.plusSeconds(backoff(intentos)));
                if (intentos >= maxAttempts) {
                    log.error("Outbox {} agotó {} intentos; se reintenta cada {} s con el mismo eventId {}",
                            evento.getId(), maxAttempts, maxBackoffSeconds, evento.getEventId());
                } else {
                    log.warn("No se pudo publicar el outbox {} (intento {}): {}",
                            evento.getEventId(), intentos, ex.getMessage());
                }
            }
        }
    }

    private long backoff(int intentos) {
        return Math.min(Math.max(1, baseBackoffSeconds) * intentos, maxBackoffSeconds);
    }

    private Message mensaje(OutboxEvent evento) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setMessageId(evento.getEventId().toString());
        propiedades.setHeader("eventId", evento.getEventId().toString());
        if (evento.getCorrelationId() != null) {
            propiedades.setHeader("correlationId", evento.getCorrelationId());
        }
        return new Message(evento.getPayload().getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private String truncar(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        return mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
    }
}
