package ar.edu.unicen.ticketya.notification.evento;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.CompradorEventoService;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume orden.pagada, orden.reintegrada y devolucion.aprobada desde
 * notification.order (docs/eventos.md §2) y genera la notificación de forma
 * idempotente; NotificationService deduplica por eventId+tipo+destinatario.
 * La proyección local de compradores solo se alimenta con compras pagadas.
 * Un mensaje inválido se rechaza sin reencolar; los errores transitorios se
 * reintentan y, agotados los intentos, terminan en notification.order.dlq.
 */
@Component
public class OrdenPagadaListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenPagadaListener.class);

    private final NotificationService notificationService;
    private final CompradorEventoService compradorEventoService;
    private final DestinatarioEmail destinatarioEmail;
    private final ObjectMapper objectMapper;

    public OrdenPagadaListener(NotificationService notificationService,
                               CompradorEventoService compradorEventoService,
                               DestinatarioEmail destinatarioEmail,
                               ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.compradorEventoService = compradorEventoService;
        this.destinatarioEmail = destinatarioEmail;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${notification.rabbit.queue:notification.order}")
    public void escuchar(OrdenEvent evento) {
        if (evento == null || evento.eventId() == null || evento.eventId().isBlank()) {
            throw rechazo("evento de orden sin eventId");
        }
        switch (evento.type() == null ? "" : evento.type()) {
            case OrdenEvent.ORDEN_PAGADA -> manejarOrdenPagada(evento);
            case OrdenEvent.ORDEN_REINTEGRADA -> manejarOrdenReintegrada(evento);
            case OrdenEvent.DEVOLUCION_APROBADA -> manejarDevolucionAprobada(evento);
            default -> throw rechazo("tipo de evento inesperado en notification.order: "
                    + evento.type());
        }
    }

    private void manejarOrdenPagada(OrdenEvent evento) {
        log.info("orden.pagada recibida: eventId={} correlationId={}",
                evento.eventId(), evento.correlationId());
        OrdenPagadaPayload payload = convertir(evento.payload(), OrdenPagadaPayload.class);
        if (payload.ordenId() == null || payload.clienteId() == null
                || payload.eventoId() == null) {
            throw rechazo("orden.pagada sin ordenId, clienteId o eventoId");
        }
        if (!OrdenPagadaPayload.ESTADO_PAGADA.equals(payload.estado())) {
            throw rechazo("orden.pagada con estado inválido: " + payload.estado());
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID clienteId = uuid(payload.clienteId(), "clienteId");
        uuid(payload.eventoId(), "eventoId");
        compradorEventoService.registrar(payload);

        Notificacion notificacion = new Notificacion();
        notificacion.setEventId(eventId);
        notificacion.setTipo(OrdenEvent.ORDEN_PAGADA);
        notificacion.setDestinatarioId(clienteId);
        notificacion.setDestinatarioEmail(destinatarioEmail.resolver(payload.clienteEmail(), clienteId));
        notificacion.setAsunto(asunto(payload));
        notificacion.setContenido(contenido(payload));
        notificacion.setCorrelationId(evento.correlationId());

        Notificacion resultado = notificationService.crearYEnviar(notificacion);
        log.info("Notificación de compra evento={} cliente={} estado={} intentos={}",
                eventId, clienteId, resultado.getEstado(), resultado.getIntentos());
    }

    private void manejarOrdenReintegrada(OrdenEvent evento) {
        OrdenReintegradaPayload payload = convertir(evento.payload(), OrdenReintegradaPayload.class);
        if (payload.ordenId() == null || payload.clienteId() == null || payload.eventoId() == null) {
            throw rechazo("orden.reintegrada sin ordenId, clienteId o eventoId");
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID clienteId = uuid(payload.clienteId(), "clienteId");
        Notificacion notificacion = new Notificacion();
        notificacion.setEventId(eventId);
        notificacion.setTipo(OrdenEvent.ORDEN_REINTEGRADA);
        notificacion.setDestinatarioId(clienteId);
        notificacion.setDestinatarioEmail(destinatarioEmail.resolver(null, clienteId));
        notificacion.setAsunto("Reintegro de la orden " + payload.ordenId());
        notificacion.setContenido(contenidoReintegro(payload));
        notificacion.setCorrelationId(evento.correlationId());

        Notificacion resultado = notificationService.crearYEnviar(notificacion);
        log.info("Notificación de reintegro evento={} cliente={} estado={} intentos={}",
                eventId, clienteId, resultado.getEstado(), resultado.getIntentos());
    }

    private void manejarDevolucionAprobada(OrdenEvent evento) {
        DevolucionAprobadaPayload payload = convertir(evento.payload(), DevolucionAprobadaPayload.class);
        if (payload.devolucionId() == null || payload.ordenId() == null || payload.clienteId() == null) {
            throw rechazo("devolucion.aprobada sin devolucionId, ordenId o clienteId");
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID clienteId = uuid(payload.clienteId(), "clienteId");
        Notificacion notificacion = new Notificacion();
        notificacion.setEventId(eventId);
        notificacion.setTipo(OrdenEvent.DEVOLUCION_APROBADA);
        notificacion.setDestinatarioId(clienteId);
        notificacion.setDestinatarioEmail(destinatarioEmail.resolver(null, clienteId));
        notificacion.setAsunto("Devolución aprobada - orden " + payload.ordenId());
        notificacion.setContenido(contenidoDevolucion(payload));
        notificacion.setCorrelationId(evento.correlationId());

        Notificacion resultado = notificationService.crearYEnviar(notificacion);
        log.info("Notificación de devolución evento={} cliente={} estado={} intentos={}",
                eventId, clienteId, resultado.getEstado(), resultado.getIntentos());
    }

    /** Convierte el JsonNode del sobre al payload específico del evento. */
    private <T> T convertir(JsonNode payload, Class<T> tipo) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw rechazo("payload ausente para " + tipo.getSimpleName());
        }
        try {
            return objectMapper.treeToValue(payload, tipo);
        } catch (JsonProcessingException ex) {
            throw rechazo("payload inválido para " + tipo.getSimpleName() + ": "
                    + ex.getOriginalMessage());
        }
    }

    private String asunto(OrdenPagadaPayload payload) {
        String nombre = payload.eventoNombre() == null ? "tu evento" : payload.eventoNombre();
        return "Compra confirmada - " + nombre;
    }

    private String contenido(OrdenPagadaPayload payload) {
        String nombre = payload.eventoNombre() == null ? "tu evento" : payload.eventoNombre();
        return "Tu compra de la orden " + payload.ordenId() + " para " + nombre + " fue confirmada.";
    }

    private String contenidoReintegro(OrdenReintegradaPayload payload) {
        StringBuilder texto = new StringBuilder("Tu compra de la orden ").append(payload.ordenId())
                .append(" fue reintegrada.");
        if (payload.monto() != null) {
            texto.append(" Monto: ").append(payload.monto().toPlainString()).append('.');
        }
        if (payload.motivo() != null && !payload.motivo().isBlank()) {
            texto.append(" Motivo: ").append(payload.motivo()).append('.');
        }
        return texto.toString();
    }

    private String contenidoDevolucion(DevolucionAprobadaPayload payload) {
        StringBuilder texto = new StringBuilder("Se aprobó la devolución de la entrada ")
                .append(payload.entradaId() == null ? "de la orden " + payload.ordenId()
                        : payload.entradaId())
                .append(" de la orden ").append(payload.ordenId()).append('.');
        if (payload.monto() != null) {
            texto.append(" El monto de ").append(payload.monto().toPlainString())
                    .append(" será reintegrado.");
        }
        return texto.toString();
    }

    private UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw rechazo("evento de orden con " + campo + " inválido: " + valor);
        }
    }

    private AmqpRejectAndDontRequeueException rechazo(String motivo) {
        log.warn("Rechazando mensaje de notification.order: {}", motivo);
        return new AmqpRejectAndDontRequeueException(motivo);
    }
}
