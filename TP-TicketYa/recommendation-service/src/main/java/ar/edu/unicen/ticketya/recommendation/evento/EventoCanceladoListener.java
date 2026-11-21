package ar.edu.unicen.ticketya.recommendation.evento;

import ar.edu.unicen.ticketya.recommendation.servicio.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume `evento.cancelado` y marca el snapshot local. Sin esto, los eventos
 * cancelados seguían apareciendo en /similar y en el fallback porque el
 * snapshot se escribía siempre como PUBLICADO.
 */
@Component
public class EventoCanceladoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoCanceladoListener.class);

    private final HistorialService historialService;

    public EventoCanceladoListener(HistorialService historialService) {
        this.historialService = historialService;
    }

    @RabbitListener(queues = "${recommendation.rabbit.event-queue:recommendation.events}")
    public void escuchar(EventoCanceladoEvent event) {
        if (event == null || !EventoCanceladoEvent.TIPO.equals(event.type())) {
            log.debug("Evento ignorado por recomendaciones: {}",
                    event == null ? "null" : event.type());
            return;
        }
        String eventoId = event.eventoId();
        if (eventoId == null || eventoId.isBlank()) {
            log.warn("evento.cancelado sin eventoId; no se actualiza el snapshot");
            return;
        }
        historialService.marcarCancelado(eventoId);
        log.info("Snapshot de {} marcado CANCELADO (eventId={}, correlationId={})",
                eventoId, event.eventId(), event.correlationId());
    }
}
