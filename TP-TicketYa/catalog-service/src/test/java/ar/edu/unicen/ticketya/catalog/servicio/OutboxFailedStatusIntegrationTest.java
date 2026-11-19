package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import ar.edu.unicen.ticketya.catalog.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.catalog.dominio.OutboxStatus;
import ar.edu.unicen.ticketya.catalog.eventos.CatalogEvents;
import ar.edu.unicen.ticketya.catalog.repositorio.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OutboxFailedStatusIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("catalog.outbox.publish-ms", () -> "3600000");
        registry.add("catalog.outbox.max-attempts", () -> "2");
        registry.add("catalog.outbox.base-backoff-seconds", () -> "0");
    }

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisher publisher;

    @Test
    void elCheckDeV6AceptaFailedYNoHaceRollbackDelLote() {
        doThrow(new RuntimeException("rabbit caído")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent evento = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                CatalogEvents.EVENTO_PUBLICADO, aggregateId, OffsetDateTime.now(), null, 1,
                "{\"eventoId\":\"" + aggregateId + "\"}", OffsetDateTime.now());
        evento.setAttempts(1);
        repository.save(evento);

        publisher.publishPending();

        assertThat(repository.findById(evento.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.FAILED);
    }
}
