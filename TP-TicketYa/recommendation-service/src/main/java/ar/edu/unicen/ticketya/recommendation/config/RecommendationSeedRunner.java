package ar.edu.unicen.ticketya.recommendation.config;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Datos de prueba idempotentes para preferencias, historial y snapshots de
 * eventos. Usa $setOnInsert: si el cliente ya modificó sus preferencias, un
 * reinicio no las pisa. Se desactiva con RECOMMENDATION_SEED_ENABLED=false.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "recommendation.seed.enabled", havingValue = "true",
        matchIfMissing = true)
public class RecommendationSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationSeedRunner.class);

    private static final String CLIENTE_FRANCO = "33333333-3333-4333-8333-333333333333";
    private static final String CLIENTE_CARO = "44444444-4444-4444-8444-444444444444";
    private static final String CLIENTE_TIAGO = "55555555-5555-4555-8555-555555555555";

    private static final Instant FESTIVAL = Instant.parse("2027-03-15T00:00:00Z");
    private static final Instant TEATRO = Instant.parse("2027-04-18T23:30:00Z");
    private static final Instant CHARLA = Instant.parse("2027-05-20T21:00:00Z");

    private final MongoTemplate mongoTemplate;

    public RecommendationSeedRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            sembrar();
            log.info("Seed de recommendation-service aplicado");
        } catch (RuntimeException ex) {
            log.warn("No se pudo aplicar el seed de recommendation-service", ex);
        }
    }

    public void sembrar() {
        sembrarPreferencias();
        sembrarEventosVistos();
        sembrarHistorial();
    }

    private void sembrarPreferencias() {
        upsertPreferencia(CLIENTE_FRANCO,
                List.of("FESTIVAL", "RECITAL"),
                List.of("Los Piojos", "Callejeros"),
                "Tandil");
        upsertPreferencia(CLIENTE_CARO,
                List.of("TEATRO"),
                List.of("Compañía Teatral Tandil"),
                "Tandil");
        upsertPreferencia(CLIENTE_TIAGO,
                List.of("CONFERENCIA", "RECITAL"),
                List.of("Dra. Ana Pérez"),
                "Tandil");
    }

    private void upsertPreferencia(String clienteId, List<String> tipos, List<String> artistas,
                                   String ciudad) {
        Query query = Query.query(Criteria.where("clienteId").is(clienteId));
        Update update = new Update()
                .setOnInsert("clienteId", clienteId)
                .setOnInsert("tipos", tipos)
                .setOnInsert("artistas", artistas)
                .setOnInsert("ciudad", ciudad)
                .setOnInsert("actualizadoEn", Instant.parse("2026-11-14T12:00:00Z"));
        mongoTemplate.upsert(query, update, Preferencia.class);
    }

    private void sembrarEventosVistos() {
        upsertEventoVisto("d0000000-0000-4000-8000-000000000001",
                "Festival de Rock Tandil 2027", "FESTIVAL",
                List.of("Los Piojos", "Callejeros"), "Tandil", FESTIVAL,
                "PUBLICADO");
        upsertEventoVisto("d0000000-0000-4000-8000-000000000002",
                "El Avaro — Teatro Universitario", "TEATRO",
                List.of("Compañía Teatral Tandil"), "Tandil", TEATRO,
                "PUBLICADO");
        upsertEventoVisto("d0000000-0000-4000-8000-000000000003",
                "Arquitectura Web 2027 — Charla abierta", "CONFERENCIA",
                List.of("Dra. Ana Pérez"), "Tandil", CHARLA,
                "PUBLICADO");
    }

    private void upsertEventoVisto(String eventoId, String nombre, String tipo,
                                   List<String> artistas, String ciudad, Instant fechaEvento,
                                   String estado) {
        Query query = Query.query(Criteria.where("_id").is(eventoId));
        Update update = new Update()
                .setOnInsert("_id", eventoId)
                .setOnInsert("nombre", nombre)
                .setOnInsert("tipo", tipo)
                .setOnInsert("artistas", artistas)
                .setOnInsert("ciudad", ciudad)
                .setOnInsert("fechaEvento", fechaEvento)
                .setOnInsert("estado", estado)
                .setOnInsert("actualizadoEn", Instant.parse("2026-11-14T12:00:00Z"));
        mongoTemplate.upsert(query, update, EventoVisto.class);
    }

    private void sembrarHistorial() {
        upsertHistorial("e0000000-0000-4000-8000-000000000001",
                "d0000000-0000-4000-8000-000000000001", CLIENTE_FRANCO, "FESTIVAL",
                List.of("Los Piojos", "Callejeros"), "Tandil", FESTIVAL,
                "f0000000-0000-4000-8000-000000000001");
        upsertHistorial("e0000000-0000-4000-8000-000000000002",
                "d0000000-0000-4000-8000-000000000003", CLIENTE_FRANCO, "CONFERENCIA",
                List.of("Dra. Ana Pérez"), "Tandil", CHARLA,
                "f0000000-0000-4000-8000-000000000002");
        upsertHistorial("e0000000-0000-4000-8000-000000000003",
                "d0000000-0000-4000-8000-000000000001", CLIENTE_CARO, "FESTIVAL",
                List.of("Los Piojos", "Callejeros"), "Tandil", FESTIVAL,
                "f0000000-0000-4000-8000-000000000003");
        upsertHistorial("e0000000-0000-4000-8000-000000000004",
                "d0000000-0000-4000-8000-000000000002", CLIENTE_CARO, "TEATRO",
                List.of("Compañía Teatral Tandil"), "Tandil", TEATRO,
                "f0000000-0000-4000-8000-000000000004");
        upsertHistorial("e0000000-0000-4000-8000-000000000005",
                "d0000000-0000-4000-8000-000000000002", CLIENTE_TIAGO, "TEATRO",
                List.of("Compañía Teatral Tandil"), "Tandil", TEATRO,
                "f0000000-0000-4000-8000-000000000005");
    }

    private void upsertHistorial(String eventId, String eventoId, String clienteId, String tipo,
                                 List<String> artistas, String ciudad, Instant fechaEvento,
                                 String ordenId) {
        Query query = Query.query(Criteria.where("eventId").is(eventId)
                .and("clienteId").is(clienteId)
                .and("eventoId").is(eventoId));
        Update update = new Update()
                .setOnInsert("eventId", eventId)
                .setOnInsert("eventoId", eventoId)
                .setOnInsert("clienteId", clienteId)
                .setOnInsert("tipo", tipo)
                .setOnInsert("artistas", artistas)
                .setOnInsert("ciudad", ciudad)
                .setOnInsert("fechaEvento", fechaEvento)
                .setOnInsert("ordenId", ordenId)
                .setOnInsert("registradoEn", Instant.parse("2026-11-14T12:00:00Z"));
        mongoTemplate.upsert(query, update, HistorialCompra.class);
    }
}
