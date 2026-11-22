package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El CHECK de V6 solo admitía PENDING/PUBLISHED y hacía rollback del lote cuando el
 * publicador marcaba FAILED. V8 corrige el CHECK; acá se valida contra PostgreSQL real
 * que un evento FAILED se persiste. La lógica de reintentos se prueba en OutboxPublisherTest.
 */
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
    }

    @Autowired
    private OutboxEventRepository repository;

    @Test
    @Transactional
    void elCheckDeV6AceptaFailedYNoHaceRollbackDelLote() {
        UUID aggregateId = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now();
        OutboxEvent evento = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                CatalogEvents.EVENTO_PUBLICADO, aggregateId, ahora, null, 1,
                "{\"eventoId\":\"" + aggregateId + "\"}", ahora);
        evento.setStatus(OutboxStatus.FAILED);
        evento.setAttempts(2);
        evento.setLastError("rabbit caído");
        repository.saveAndFlush(evento);

        assertThat(repository.findById(evento.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.FAILED);
    }
}
