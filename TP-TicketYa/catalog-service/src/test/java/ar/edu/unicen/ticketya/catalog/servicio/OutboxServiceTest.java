package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.catalog.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.catalog.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
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

    @Mock
    private OutboxEventRepository repository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(repository, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void elSobreConservaLaCorrelacionRecibida() {
        String correlationId = "5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f";

        outboxService.register("evento.publicado", UUID.randomUUID(), correlationId, Map.of("nombre", "Festival"));

        OutboxEvent guardado = eventoGuardado();
        assertThat(guardado.getCorrelationId()).isEqualTo(correlationId);
        assertThat(guardado.getPayload()).contains("\"correlationId\":\"" + correlationId + "\"");
        assertThat(guardado.getPayload()).doesNotContain("correlationId\":null");
    }

    @Test
    void sinCorrelacionGeneraUnUuidEnLugarDeNull() {
        outboxService.register("evento.publicado", UUID.randomUUID(), null, Map.of("nombre", "Festival"));

        OutboxEvent guardado = eventoGuardado();
        assertThat(guardado.getCorrelationId()).isNotNull();
        assertThat(UUID.fromString(guardado.getCorrelationId())).isNotNull();
        assertThat(guardado.getPayload()).doesNotContain("correlationId\":null");
    }

    private OutboxEvent eventoGuardado() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
