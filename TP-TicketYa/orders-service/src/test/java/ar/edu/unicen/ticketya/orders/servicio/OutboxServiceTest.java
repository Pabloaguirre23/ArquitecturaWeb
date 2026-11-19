package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.orders.eventos.CorrelationContext;
import ar.edu.unicen.ticketya.orders.eventos.EventEnvelope;
import ar.edu.unicen.ticketya.orders.eventos.OrderDomainEvents;
import ar.edu.unicen.ticketya.orders.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository repository;

    private OutboxService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CorrelationContext.set("5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f");
        service = new OutboxService(repository, mapper, CLOCK);
    }

    @Test
    void registraElSobreCompletoEnElOutbox() {
        UUID ordenId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("ordenId", ordenId.toString(), "estado", "PAGADA");

        service.registrar(OrderDomainEvents.ORDEN_PAGADA, ordenId, payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent evento = captor.getValue();
        assertThat(evento.getType()).isEqualTo("orden.pagada");
        assertThat(evento.getAggregateId()).isEqualTo(ordenId);
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getCorrelationId()).isEqualTo("5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f");
        assertThat(evento.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:00Z"));
        assertThat(evento.getEventId()).isEqualTo(OutboxService.eventId("orden.pagada", ordenId));

        Map<String, Object> sobre = leer(evento.getPayload());
        assertThat(sobre).containsKeys("eventId", "type", "occurredAt", "correlationId", "version", "payload");
        assertThat(sobre.get("eventId")).isEqualTo(evento.getEventId().toString());
        assertThat(sobre.get("occurredAt")).isEqualTo("2026-11-15T12:00:00Z");
    }

    @Test
    void elEventIdEsEstableParaReintentosDelMismoHecho() {
        UUID ordenId = UUID.randomUUID();

        assertThat(OutboxService.eventId("orden.pagada", ordenId))
                .isEqualTo(OutboxService.eventId("orden.pagada", ordenId))
                .isNotEqualTo(OutboxService.eventId("orden.reintegrada", ordenId));
    }

    @Test
    void unEventoYaRegistradoNoSeVuelveAInsertar() {
        UUID ordenId = UUID.randomUUID();
        when(repository.existsByEventId(OutboxService.eventId("orden.pagada", ordenId))).thenReturn(true);

        service.registrar(OrderDomainEvents.ORDEN_PAGADA, ordenId, Map.of("ordenId", ordenId.toString()));

        verify(repository, never()).save(any());
    }

    @Test
    void sinSolicitudGeneraUnaCorrelacionNueva() {
        CorrelationContext.clear();
        UUID ordenId = UUID.randomUUID();

        service.registrar(OrderDomainEvents.ORDEN_PAGADA, ordenId, Map.of("ordenId", ordenId.toString()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isNotBlank();
    }

    private Map<String, Object> leer(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
