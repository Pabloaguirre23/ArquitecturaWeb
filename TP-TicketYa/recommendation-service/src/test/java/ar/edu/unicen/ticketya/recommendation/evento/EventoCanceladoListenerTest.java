package ar.edu.unicen.ticketya.recommendation.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.unicen.ticketya.recommendation.servicio.HistorialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventoCanceladoListenerTest {

    private static final String EVENTO = "d0000000-0000-4000-8000-000000000001";

    @Mock
    private HistorialService historialService;

    private EventoCanceladoListener listener;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new EventoCanceladoListener(historialService);
    }

    @Test
    void eventoCanceladoMarcaElSnapshot() throws Exception {
        JsonNode payload = mapper.readTree("{\"eventoId\":\"" + EVENTO + "\",\"estado\":\"CANCELADO\"}");
        EventoCanceladoEvent event = new EventoCanceladoEvent("evt-1", EventoCanceladoEvent.TIPO,
                Instant.now(), "corr-1", 1, payload);

        listener.escuchar(event);

        verify(historialService).marcarCancelado(EVENTO);
    }

    @Test
    void otroTipoDeEventoSeIgnora() throws Exception {
        JsonNode payload = mapper.readTree("{\"eventoId\":\"" + EVENTO + "\"}");
        EventoCanceladoEvent event = new EventoCanceladoEvent("evt-2", "evento.publicado",
                Instant.now(), "corr-1", 1, payload);

        listener.escuchar(event);

        verifyNoInteractions(historialService);
    }

    @Test
    void unCanceladoSinEventoIdNoRompeElConsumidor() throws Exception {
        EventoCanceladoEvent event = new EventoCanceladoEvent("evt-3", EventoCanceladoEvent.TIPO,
                Instant.now(), "corr-1", 1, mapper.readTree("{}"));

        listener.escuchar(event);

        assertThat(event.eventoId()).isNull();
        verifyNoInteractions(historialService);
    }
}
