package ar.edu.unicen.ticketya.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.repositorio.NotificacionRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifica que el esqueleto arranque con PostgreSQL y RabbitMQ reales.
 * Se omite automáticamente si Docker no está corriendo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationServiceApplicationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("notification_db")
            .withUsername("ticketya")
            .withPassword("ticketya");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withAdminUser("ticketya")
            .withAdminPassword("ticketya");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Test
    void elHealthReportaBaseYBrokerConectados() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> health = response.getBody();
        assertThat(health).isNotNull();
        assertThat(health.get("status")).isEqualTo("UP");
        Map<?, ?> components = (Map<?, ?>) health.get("components");
        assertThat(components).isNotNull();
        assertThat(((Map<?, ?>) components.get("db")).get("status")).isEqualTo("UP");
        assertThat(((Map<?, ?>) components.get("rabbit")).get("status")).isEqualTo("UP");
    }

    /**
     * Mensaje venenoso con broker real: una orden.pagada con estado inválido se
     * rechaza sin reencolar, agota los reintentos del consumidor y termina en
     * notification.order.dlq sin generar notificaciones.
     */
    @Test
    void unMensajeVenenosoTerminaEnLaDlqSinNotificar() {
        UUID eventId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ordenId", UUID.randomUUID().toString());
        payload.put("clienteId", clienteId.toString());
        payload.put("eventoId", UUID.randomUUID().toString());
        payload.put("estado", "PENDIENTE");
        Map<String, Object> sobre = new LinkedHashMap<>();
        sobre.put("eventId", eventId.toString());
        sobre.put("type", "orden.pagada");
        sobre.put("occurredAt", "2026-11-15T12:00:00Z");
        sobre.put("correlationId", eventId.toString());
        sobre.put("version", 1);
        sobre.put("payload", payload);

        rabbitTemplate.convertAndSend("ticketya.events", "orden.pagada", sobre);

        org.springframework.amqp.core.Message muerto = rabbitTemplate.receive("notification.order.dlq", 20_000);
        assertThat(muerto).isNotNull();
        assertThat(new String(muerto.getBody())).contains(eventId.toString());
        Optional<Notificacion> notificacion = notificacionRepository
                .findByEventIdAndTipoAndDestinatarioId(eventId, "orden.pagada", clienteId);
        assertThat(notificacion).isEmpty();
    }

    /**
     * Regresión de auditoría: updated_at debe reflejar el último cambio y no
     * quedar congelado en el valor del INSERT (columna con DEFAULT now()).
     */
    @Test
    void actualizarUnaNotificacionMueveUpdatedAt() throws InterruptedException {
        Notificacion notificacion = new Notificacion();
        notificacion.setEventId(UUID.randomUUID());
        notificacion.setTipo("orden.pagada");
        notificacion.setDestinatarioId(UUID.randomUUID());
        notificacion.setDestinatarioEmail("cliente@example.com");
        notificacion.setAsunto("asunto");
        notificacion.setContenido("contenido");
        notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        Notificacion creada = notificacionRepository.save(notificacion);

        Notificacion antes = notificacionRepository.findById(creada.getId()).orElseThrow();
        assertThat(antes.getUpdatedAt()).isNotNull();

        Thread.sleep(50);
        antes.setEstado(EstadoNotificacion.ENVIADA);
        antes.setEnviadaAt(OffsetDateTime.now());
        notificacionRepository.save(antes);

        Notificacion despues = notificacionRepository.findById(creada.getId()).orElseThrow();
        assertThat(despues.getUpdatedAt()).isAfter(antes.getUpdatedAt());
    }
}
