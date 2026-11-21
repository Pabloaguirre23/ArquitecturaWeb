package ar.edu.unicen.ticketya.recommendation.servicio;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaEvent;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaPayload;
import ar.edu.unicen.ticketya.recommendation.repositorio.HistorialCompraRepository;
import com.mongodb.client.result.UpdateResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Historial de compras pagadas para recomendaciones.
 *
 * La idempotencia se apoya en el índice único
 * (eventId, clienteId, eventoId): el upsert con $setOnInsert no modifica un
 * documento ya registrado si el mismo evento de dominio se entrega dos veces.
 */
@Service
public class HistorialService {

    private static final Logger log = LoggerFactory.getLogger(HistorialService.class);

    private final MongoTemplate mongoTemplate;
    private final HistorialCompraRepository historialRepository;
    private final Clock clock;

    public HistorialService(MongoTemplate mongoTemplate,
                            HistorialCompraRepository historialRepository,
                            Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.historialRepository = historialRepository;
        this.clock = clock;
    }

    /**
     * Registra una compra pagada. Devuelve true solo cuando insertó un documento
     * nuevo; un duplicado o un evento no pagado devuelve false sin duplicar.
     */
    public boolean registrar(OrdenPagadaEvent event) {
        if (event == null || !OrdenPagadaEvent.TIPO.equals(event.type())) {
            log.debug("Evento ignorado por el historial: {}", event == null ? "null" : event.type());
            return false;
        }
        OrdenPagadaPayload payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("El evento orden.pagada no tiene payload");
        }
        if (!OrdenPagadaPayload.ESTADO_PAGADA.equals(payload.estado())) {
            // El contrato exige estado=PAGADA; un payload sin estado no es una compra
            // pagada y no debe contaminar historial, popularidad ni co-compra.
            log.info("Compra {} no está pagada ({}); no se registra en el historial",
                    payload.ordenId(), payload.estado());
            return false;
        }

        String eventId = uuidRequerido(event.eventId(), "eventId").toString();
        String ordenId = uuidRequerido(payload.ordenId(), "ordenId").toString();
        String clienteId = uuidRequerido(payload.clienteId(), "clienteId").toString();
        String eventoId = uuidRequerido(payload.eventoId(), "eventoId").toString();

        Query query = Query.query(Criteria.where("eventId").is(eventId)
                .and("clienteId").is(clienteId)
                .and("eventoId").is(eventoId));
        Update update = new Update()
                .setOnInsert("eventId", eventId)
                .setOnInsert("eventoId", eventoId)
                .setOnInsert("clienteId", clienteId)
                .setOnInsert("tipo", payload.eventoTipo())
                .setOnInsert("artistas", payload.artistas() == null ? List.of() : payload.artistas())
                .setOnInsert("ciudad", payload.ciudad())
                .setOnInsert("fechaEvento", payload.fechaEvento())
                .setOnInsert("ordenId", ordenId)
                .setOnInsert("registradoEn", Instant.now(clock));

        UpdateResult result = mongoTemplate.upsert(query, update, HistorialCompra.class);
        boolean creado = result.getMatchedCount() == 0;
        if (creado) {
            log.info("Historial registrado para cliente {} evento {} (eventId {})",
                    clienteId, eventoId, eventId);
        } else {
            log.info("Evento {} ya estaba en el historial; no se duplica", eventId);
        }

        // El snapshot eventos_vistos alimenta similares y el fallback de 7.4.
        // Sin esto solo existirían los eventos del seed.
        registrarEventoVisto(payload, eventoId);
        return creado;
    }

    private void registrarEventoVisto(OrdenPagadaPayload payload, String eventoId) {
        Query query = Query.query(Criteria.where("_id").is(eventoId));
        Update update = new Update()
                .set("nombre", payload.eventoNombre())
                .set("tipo", payload.eventoTipo())
                .set("artistas", payload.artistas() == null ? List.of() : payload.artistas())
                .set("ciudad", payload.ciudad())
                .set("fechaEvento", payload.fechaEvento())
                // Solo al crear: si el evento ya fue cancelado, un orden.pagada
                // reintentado no debe revivirlo como PUBLICADO.
                .setOnInsert("estado", "PUBLICADO")
                .set("actualizadoEn", Instant.now(clock));
        mongoTemplate.upsert(query, update, EventoVisto.class);
    }

    /**
     * Marca el snapshot de un evento cancelado. Es idempotente y tolera que el
     * evento todavía no tenga snapshot (nunca se vendió): en ese caso no hay
     * nada que recomendar.
     */
    public void marcarCancelado(String eventoId) {
        Query query = Query.query(Criteria.where("_id").is(eventoId));
        Update update = new Update()
                .set("estado", "CANCELADO")
                .set("actualizadoEn", Instant.now(clock));
        mongoTemplate.updateFirst(query, update, EventoVisto.class);
    }

    public List<HistorialCompra> historial(UUID clienteId) {
        return historialRepository.findByClienteId(clienteId.toString());
    }

    private UUID uuidRequerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + campo + " en el evento orden.pagada");
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("El campo " + campo + " no es un UUID válido: " + valor, ex);
        }
    }
}
