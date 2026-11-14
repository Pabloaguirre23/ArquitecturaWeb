package ar.edu.unicen.ticketya.recommendation.config;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Crea los índices de la decisión 3 del design.md de forma idempotente.
 * Se ejecuta al arrancar para que una base nueva quede lista sin depender de
 * auto-index-creation (deshabilitado en producción).
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "recommendation.mongo.ensure-indexes", havingValue = "true",
        matchIfMissing = true)
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            crearIndices();
            log.info("Índices de recommendation-service verificados");
        } catch (RuntimeException ex) {
            // La app sigue arrancando para que /actuator/health exponga el fallo
            // de MongoDB; el compose espera el healthcheck antes de usarla.
            log.error("No se pudieron crear los índices de MongoDB", ex);
        }
    }

    public void crearIndices() {
        IndexOperations preferencias = mongoTemplate.indexOps(Preferencia.class);
        preferencias.ensureIndex(new Index()
                .on("clienteId", Sort.Direction.ASC)
                .unique()
                .named("uk_preferencias_cliente"));
        preferencias.ensureIndex(new Index()
                .on("ciudad", Sort.Direction.ASC)
                .named("idx_preferencias_ciudad"));

        IndexOperations historial = mongoTemplate.indexOps(HistorialCompra.class);
        historial.ensureIndex(new Index()
                .on("eventId", Sort.Direction.ASC)
                .on("clienteId", Sort.Direction.ASC)
                .on("eventoId", Sort.Direction.ASC)
                .unique()
                .named("uk_historial_evento_cliente_evento"));
        historial.ensureIndex(new Index()
                .on("clienteId", Sort.Direction.ASC)
                .on("fechaEvento", Sort.Direction.ASC)
                .named("idx_historial_cliente_fecha"));
        historial.ensureIndex(new Index()
                .on("eventoId", Sort.Direction.ASC)
                .on("clienteId", Sort.Direction.ASC)
                .named("idx_historial_evento_cliente"));

        IndexOperations eventos = mongoTemplate.indexOps(EventoVisto.class);
        eventos.ensureIndex(new Index()
                .on("ciudad", Sort.Direction.ASC)
                .on("tipo", Sort.Direction.ASC)
                .named("idx_eventos_vistos_ciudad_tipo"));
        eventos.ensureIndex(new Index()
                .on("fechaEvento", Sort.Direction.ASC)
                .named("idx_eventos_vistos_fecha"));
    }
}
