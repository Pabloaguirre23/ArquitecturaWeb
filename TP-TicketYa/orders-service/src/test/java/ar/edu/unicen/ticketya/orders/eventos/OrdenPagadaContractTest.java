package ar.edu.unicen.ticketya.orders.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.orders.dominio.Orden;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Test de contrato del productor (tarea 6.3): el sobre y el payload que genera
 * orders deben respetar el fixture compartido de `docs/eventos.md`.
 */
class OrdenPagadaContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void elSobreGeneradoRespetaElFormatoDelFixture() throws IOException {
        Map<String, Object> fixture = leerFixture();

        EventEnvelope envelope = EventEnvelope.of(
                UUID.fromString((String) fixture.get("eventId")),
                (String) fixture.get("type"),
                OffsetDateTime.parse((String) fixture.get("occurredAt")),
                (String) fixture.get("correlationId"),
                castearPayload(fixture.get("payload")));

        String json = mapper.writeValueAsString(envelope.asMap());
        Map<String, Object> generado = mapper.readValue(json, new TypeReference<>() {
        });

        assertThat(generado).containsExactlyInAnyOrderEntriesOf(fixture);
        assertThat(generado.get("version")).isEqualTo(1);
    }

    @Test
    void elPayloadDeOrdenPagadaIncluyeTipoArtistasYCiudadDelSnapshot() throws IOException {
        Map<String, Object> fixture = leerFixture();
        Map<String, Object> esperado = castearPayload(fixture.get("payload"));

        Orden orden = new Orden();
        orden.setId(UUID.fromString((String) esperado.get("ordenId")));
        orden.setClienteId(UUID.fromString((String) esperado.get("clienteId")));
        orden.setClienteEmail((String) esperado.get("clienteEmail"));
        orden.setEventoId(UUID.fromString((String) esperado.get("eventoId")));
        orden.setEventoNombre((String) esperado.get("eventoNombre"));
        orden.setEventoTipo((String) esperado.get("eventoTipo"));
        orden.setEventoArtistas("[\"Los Piojos\", \"Callejeros\"]");
        orden.setEventoCiudad((String) esperado.get("ciudad"));
        orden.setEventoFecha(OffsetDateTime.parse((String) esperado.get("fechaEvento")));
        orden.setTotal(new BigDecimal("25000.00"));

        Map<String, Object> payload = OrderDomainEvents.ordenPagada(orden);

        assertThat(payload).containsExactlyInAnyOrderEntriesOf(esperado);
        assertThat(payload.get("artistas")).isEqualTo(List.of("Los Piojos", "Callejeros"));
        assertThat(payload.get("estado")).isEqualTo("PAGADA");
    }

    private Map<String, Object> leerFixture() throws IOException {
        try (InputStream entrada = getClass().getResourceAsStream("/eventos/orden-pagada.json")) {
            assertThat(entrada).isNotNull();
            return mapper.readValue(entrada, new TypeReference<>() {
            });
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castearPayload(Object valor) {
        return (Map<String, Object>) valor;
    }
}
