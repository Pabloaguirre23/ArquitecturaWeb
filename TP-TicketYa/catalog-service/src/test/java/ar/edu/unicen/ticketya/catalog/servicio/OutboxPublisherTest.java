package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.catalog.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.catalog.dominio.OutboxStatus;
import ar.edu.unicen.ticketya.catalog.eventos.CatalogEvents;
import ar.edu.unicen.ticketya.catalog.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, rabbitTemplate, new ObjectMapper(), CLOCK, 50, 3, 5);
    }

    @Test
    void publicaElEventoYLoMarcaComoPublicado() {
        OutboxEvent evento = evento(0);
        when(repository.lockPending(NOW, 50)).thenReturn(List.of(evento));

        publisher.publishPending();

        verify(rabbitTemplate).convertAndSend(CatalogEvents.EXCHANGE, CatalogEvents.EVENTO_PUBLICADO,
                java.util.Map.of("eventoId", evento.getEventId().toString()));
        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(evento.getPublishedAt()).isEqualTo(NOW);
        assertThat(evento.getLastError()).isNull();
        assertThat(evento.getAttempts()).isZero();
    }

    @Test
    void conRabbitCaidoElEventoQuedaPendienteYConservaElMismoEventId() {
        OutboxEvent evento = evento(1);
        when(repository.lockPending(NOW, 50)).thenReturn(List.of(evento));
        doThrow(new RuntimeException("rabbit caído")).when(rabbitTemplate)
                .convertAndSend(eq(CatalogEvents.EXCHANGE), eq(CatalogEvents.EVENTO_PUBLICADO), any(Object.class));

        publisher.publishPending();

        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(evento.getAttempts()).isEqualTo(2);
        assertThat(evento.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(evento.getLastError()).contains("rabbit");
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void alAgotarReintentosQuedaFailedSinRomperElRestoDelLote() {
        OutboxEvent agotado = evento(2, CatalogEvents.EVENTO_PUBLICADO);
        OutboxEvent publicado = evento(0, CatalogEvents.EVENTO_ACTUALIZADO);
        when(repository.lockPending(NOW, 50)).thenReturn(List.of(agotado, publicado));
        doThrow(new RuntimeException("rabbit caído")).when(rabbitTemplate)
                .convertAndSend(eq(CatalogEvents.EXCHANGE), eq(CatalogEvents.EVENTO_PUBLICADO), any(Object.class));

        assertThatCode(publisher::publishPending).doesNotThrowAnyException();

        assertThat(agotado.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(agotado.getAttempts()).isEqualTo(3);
        assertThat(agotado.getLastError()).contains("rabbit");
        assertThat(publicado.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publicado.getPublishedAt()).isEqualTo(NOW);
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    private OutboxEvent evento(int intentosPrevios) {
        return evento(intentosPrevios, CatalogEvents.EVENTO_PUBLICADO);
    }

    private OutboxEvent evento(int intentosPrevios, String type) {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventoId\":\"" + eventId + "\"}";
        OutboxEvent evento = new OutboxEvent(UUID.randomUUID(), eventId, type,
                eventId, NOW, "correlacion", 1, payload, NOW);
        evento.setAttempts(intentosPrevios);
        return evento;
    }
}
