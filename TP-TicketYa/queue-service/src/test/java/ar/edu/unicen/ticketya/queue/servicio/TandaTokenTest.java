package ar.edu.unicen.ticketya.queue.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "queue.scheduler-enabled=false")
class TandaTokenTest {

    private static final String CLIENTE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CLIENTE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String CLIENTE_C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("queue.batch-size", () -> "2");
        registry.add("queue.intervalo-segundos", () -> "30");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private FilaService filaService;

    @Autowired
    private QueueTokenService tokens;

    @Autowired
    private Clock clock;

    @BeforeEach
    void limpiar() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void laTandaHabilitaEnOrdenYNoRepiteAlMismoCliente() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        ingresar(evento, CLIENTE_A);
        ingresar(evento, CLIENTE_B);
        ingresar(evento, CLIENTE_C);

        Instant primera = clock.instant().plusSeconds(31);
        filaService.procesarTandas(primera);

        Posicion a = consultar(evento, CLIENTE_A);
        Posicion b = consultar(evento, CLIENTE_B);
        Posicion c = consultar(evento, CLIENTE_C);
        assertThat(a.estado()).isEqualTo("HABILITADO");
        assertThat(b.estado()).isEqualTo("HABILITADO");
        assertThat(c.estado()).isEqualTo("EN_ESPERA");
        assertThat(c.posicion()).isEqualTo(1);
        assertThat(a.token()).isNotBlank();
        assertThat(b.token()).isNotBlank();
        assertThat(c.token()).isNull();

        Claims claims = tokens.leer(a.token());
        assertThat(claims.get("eventId", String.class)).isEqualTo(evento.toString());
        assertThat(claims.getSubject()).isEqualTo(CLIENTE_A);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("type", String.class)).isEqualTo("QUEUE");
        assertThat(claims.getExpiration().toInstant()).isAfter(primera);

        filaService.procesarTandas(primera);
        assertThat(consultar(evento, CLIENTE_A).token()).isEqualTo(a.token());
        assertThat(consultar(evento, CLIENTE_C).estado()).isEqualTo("EN_ESPERA");

        filaService.procesarTandas(primera.plusSeconds(31));
        assertThat(consultar(evento, CLIENTE_C).estado()).isEqualTo("HABILITADO");
        assertThat(consultar(evento, CLIENTE_A).token()).isEqualTo(a.token());
        assertThat(redis.opsForHash().size(FilaService.admitidosKey(evento))).isEqualTo(3);
    }

    @Test
    void unaTandaVaciaNoEmiteTokensYElTokenVencidoDejaDeServir() {
        UUID evento = UUID.randomUUID();
        abrir(evento);

        Instant primera = clock.instant().plusSeconds(31);
        filaService.procesarTandas(primera);
        assertThat(redis.hasKey(FilaService.admitidosKey(evento))).isFalse();

        ingresar(evento, CLIENTE_A);
        filaService.procesarTandas(primera.plusSeconds(31));
        Posicion habilitado = consultar(evento, CLIENTE_A);
        assertThat(habilitado.estado()).isEqualTo("HABILITADO");

        Instant vencido = tokens.leer(habilitado.token()).getExpiration().toInstant().plusSeconds(1);
        filaService.procesarTandas(vencido);

        ResponseEntity<String> respuesta = rest.exchange(
                "/api/queue/events/" + evento + "/position",
                HttpMethod.GET,
                new HttpEntity<>(identidad(CLIENTE_A)),
                String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).contains("NOT_IN_QUEUE");
    }

    @Test
    void unPendienteSinTokenSeFirmaAlConsultarYNoCambia() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        dejarPendiente(evento, CLIENTE_A);

        Posicion primera = consultar(evento, CLIENTE_A);
        assertThat(primera.estado()).isEqualTo("HABILITADO");
        assertThat(primera.token()).isNotBlank();
        assertThat(redis.opsForHash().get(FilaService.admitidosKey(evento), CLIENTE_A))
                .isNotEqualTo(FilaService.PENDIENTE);
        assertThat(consultar(evento, CLIENTE_A).token()).isEqualTo(primera.token());
    }

    @Test
    void elSchedulerFirmaUnPendienteQueQuedoSinToken() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        dejarPendiente(evento, CLIENTE_A);

        filaService.procesarTandas(clock.instant());

        Object valor = redis.opsForHash().get(FilaService.admitidosKey(evento), CLIENTE_A);
        assertThat(valor).isNotEqualTo(FilaService.PENDIENTE);
        assertThat(valor.toString().split("\\|")).hasSize(3);
        assertThat(consultar(evento, CLIENTE_A).estado()).isEqualTo("HABILITADO");
    }

    private void dejarPendiente(UUID evento, String cliente) {
        ingresar(evento, cliente);
        redis.opsForZSet().remove(FilaService.esperaKey(evento), cliente);
        redis.opsForHash().put(FilaService.admitidosKey(evento), cliente, FilaService.PENDIENTE);
    }

    private void abrir(UUID evento) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", "ticketya-internal-dev");
        ResponseEntity<String> respuesta = rest.exchange(
                "/internal/events/" + evento,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void ingresar(UUID evento, String cliente) {
        ResponseEntity<Posicion> respuesta = rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(identidad(cliente)),
                Posicion.class);
        assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private Posicion consultar(UUID evento, String cliente) {
        ResponseEntity<Posicion> respuesta = rest.exchange(
                "/api/queue/events/" + evento + "/position",
                HttpMethod.GET,
                new HttpEntity<>(identidad(cliente)),
                Posicion.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        return respuesta.getBody();
    }

    private static HttpHeaders identidad(String cliente) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", cliente);
        headers.set("X-User-Role", "CLIENT");
        return headers;
    }

    private record Posicion(String estado, int posicion, String token) {
    }
}
