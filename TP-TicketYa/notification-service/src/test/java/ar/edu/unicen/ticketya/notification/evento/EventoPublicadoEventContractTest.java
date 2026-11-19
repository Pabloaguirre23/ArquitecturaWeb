package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventoPublicadoEventContractTest {

    @Test
    void deserializaElFixtureCompartidoDeEventoPublicado() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Path fixture = Path.of("..", "docs", "ejemplos", "evento-publicado.json");

        EventoEvent evento = mapper.readValue(fixture.toFile(), EventoEvent.class);

        assertThat(evento.eventId()).isEqualTo("e0000000-0000-4000-8000-000000000002");
        assertThat(evento.type()).isEqualTo(EventoEvent.PUBLICADO);
        assertThat(evento.correlationId()).isEqualTo("5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f");
        assertThat(evento.version()).isEqualTo(1);
        assertThat(evento.payload()).isNotNull();
        assertThat(evento.payload().artistas()).containsExactly("Los Piojos", "Callejeros");
        assertThat(evento.payload().ciudad()).isEqualTo("Tandil");
        assertThat(evento.payload().fechaEvento()).isNotNull();
        assertThat(evento.payload().estado()).isEqualTo("PUBLICADO");
    }
}
