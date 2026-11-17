package ar.edu.unicen.ticketya.catalog.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventoPublicadoEventContractTest {

    @Test
    void elFixtureCompartidoDefineElPayloadQueCatalogDebePublicar() throws Exception {
        JsonNode envelope = new ObjectMapper().readTree(
                Path.of("..", "docs", "ejemplos", "evento-publicado.json").toFile());
        JsonNode payload = envelope.at("/payload");

        assertThat(envelope.at("/type").asText()).isEqualTo("evento.publicado");
        assertThat(payload.has("eventoId")).isTrue();
        assertThat(payload.has("nombre")).isTrue();
        assertThat(payload.has("tipo")).isTrue();
        assertThat(payload.has("artistas")).isTrue();
        assertThat(payload.at("/artistas").size()).isEqualTo(2);
        assertThat(payload.has("ciudad")).isTrue();
        assertThat(payload.has("fechaEvento")).isTrue();
        assertThat(payload.has("organizadorId")).isTrue();
        assertThat(payload.has("estado")).isTrue();
        assertThat(payload.has("fecha")).isFalse();
        assertThat(payload.has("lugarId")).isFalse();

        EventEnvelope sobre = new EventEnvelope(UUID.randomUUID(), envelope.at("/type").asText(),
                envelope.at("/occurredAt").asText(), envelope.at("/correlationId").asText(), 1,
                new ObjectMapper().convertValue(payload, java.util.Map.class));
        assertThat(sobre.asMap().get("correlationId")).isEqualTo(envelope.at("/correlationId").asText());
    }
}
