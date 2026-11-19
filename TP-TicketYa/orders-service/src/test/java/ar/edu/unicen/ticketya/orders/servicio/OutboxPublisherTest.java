package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOutbox;
import ar.edu.unicen.ticketya.orders.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.orders.repositorio.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, rabbitTemplate, CLOCK,
                "ticketya.events", 50, 2, 5, 300);
    }

    @Test
    void publicaElSobrePorLaRoutingKeyYLoMarcaComoPublicado() {
        OutboxEvent evento = evento("orden.pagada");
        when(repository.lockPending(OffsetDateTime.parse("2026-11-15T12:00:00Z"), 50))
                .thenReturn(List.of(evento));

        publisher.publishPending();

        ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("ticketya.events"), eq("orden.pagada"), mensaje.capture());
        assertThat(new String(mensaje.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(evento.getPayload());
        assertThat(mensaje.getValue().getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(mensaje.getValue().getMessageProperties().getMessageId())
                .isEqualTo(evento.getEventId().toString());
        assertThat(evento.getStatus()).isEqualTo(EstadoOutbox.PUBLISHED);
        assertThat(evento.getPublishedAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:00Z"));
        assertThat(evento.getAttempts()).isZero();
    }

    @Test
    void siRabbitFallaElEventoQuedaPendienteConBackoffYMismoEventId() {
        OutboxEvent evento = evento("orden.reintegrada");
        when(repository.lockPending(any(), eq(50))).thenReturn(List.of(evento));
        doThrow(new IllegalStateException("broker caído"))
                .when(rabbitTemplate).send(eq("ticketya.events"), eq("orden.reintegrada"), any(Message.class));

        publisher.publishPending();

        assertThat(evento.getStatus()).isEqualTo(EstadoOutbox.PENDING);
        assertThat(evento.getAttempts()).isEqualTo(1);
        assertThat(evento.getLastError()).isEqualTo("broker caído");
        assertThat(evento.getNextAttemptAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:05Z"));
        assertThat(evento.getEventId()).isNotNull();
        verify(repository, never()).save(evento);
    }

    @Test
    void agotarLosIntentosNoBorraElEvento() {
        OutboxEvent evento = evento("devolucion.aprobada");
        evento.setAttempts(2);
        when(repository.lockPending(any(), eq(50))).thenReturn(List.of(evento));
        doThrow(new IllegalStateException("broker caído"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        publisher.publishPending();

        assertThat(evento.getStatus()).isEqualTo(EstadoOutbox.PENDING);
        assertThat(evento.getAttempts()).isEqualTo(3);
        assertThat(evento.getNextAttemptAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:15Z"));
    }

    @Test
    void sinPendientesNoPublicaNada() {
        when(repository.lockPending(any(), eq(50))).thenReturn(List.of());

        publisher.publishPending();

        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    private OutboxEvent evento(String type) {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.parse("2026-11-15T12:00:00Z");
        String payload = """
                {"eventId":"%s","type":"%s","occurredAt":"2026-11-15T12:00:00Z",
                 "correlationId":"corr-1","version":1,"payload":{}}
                """.formatted(eventId, type);
        return new OutboxEvent(UUID.randomUUID(), eventId, type, UUID.randomUUID(), ahora,
                "corr-1", 1, payload, ahora);
    }
}
