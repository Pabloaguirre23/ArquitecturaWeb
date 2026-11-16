package ar.edu.unicen.ticketya.queue.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
class CierreFilaTest {

    private static final String CLIENTE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CLIENTE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "always");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("queue.batch-size", () -> "1");
        registry.add("queue.intervalo-segundos", () -> "30");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LettuceConnectionFactory conexiones;

    @Autowired
    private FilaService filaService;

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
    void cerrarCortaIngresosTokensYLaConsultaQuedaCerrada() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        ingresar(evento, CLIENTE_A);
        ingresar(evento, CLIENTE_B);
        filaService.procesarTandas(clock.instant().plusSeconds(31));
        assertThat(consultar(evento, CLIENTE_A).estado()).isEqualTo("HABILITADO");

        Estado cierre = cerrar(evento, "CANCELADO");
        assertThat(cierre.estado()).isEqualTo("CERRADA");
        assertThat(cierre.motivo()).isEqualTo("CANCELADO");

        Estado repetido = cerrar(evento, "AGOTADO");
        assertThat(repetido.motivo()).isEqualTo("CANCELADO");

        Posicion espera = consultar(evento, CLIENTE_B);
        Posicion habilitado = consultar(evento, CLIENTE_A);
        assertThat(espera.estado()).isEqualTo("CERRADA");
        assertThat(espera.token()).isNull();
        assertThat(espera.posicion()).isEqualTo(1);
        assertThat(habilitado.estado()).isEqualTo("CERRADA");
        assertThat(habilitado.token()).isNull();

        ResponseEntity<String> ingreso = rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(identidad("dddddddd-dddd-4ddd-8ddd-dddddddddddd")),
                String.class);
        assertThat(ingreso.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ingreso.getBody()).contains("QUEUE_CLOSED");

        filaService.procesarTandas(clock.instant().plusSeconds(120));
        assertThat(consultar(evento, CLIENTE_B).estado()).isEqualTo("CERRADA");
        assertThat(redis.opsForSet().isMember(FilaService.OPEN_KEY, evento.toString())).isFalse();
        assertThat(redis.opsForHash().get(FilaService.admitidosKey(evento), CLIENTE_A)).isEqualTo("CERRADA");
    }

    @Test
    void sinFilaElCierreResponde404YUnMotivoInvalido400() {
        ResponseEntity<String> ausente = rest.exchange(
                "/internal/events/" + UUID.randomUUID() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("motivo", "FINALIZADO"), token()),
                String.class);
        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ausente.getBody()).contains("QUEUE_NOT_OPEN");

        UUID evento = UUID.randomUUID();
        abrir(evento);
        ResponseEntity<String> invalido = rest.exchange(
                "/internal/events/" + evento + "/close",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("motivo", "PAUSA"), token()),
                String.class);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalido.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void siRedisPierdeElEstadoNoSeEmitenTokens() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        ingresar(evento, CLIENTE_A);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        ResponseEntity<String> ingreso = rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(identidad(CLIENTE_B)),
                String.class);
        assertThat(ingreso.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ingreso.getBody()).contains("QUEUE_NOT_OPEN");

        filaService.procesarTandas(clock.instant().plusSeconds(120));
        assertThat(redis.hasKey(FilaService.admitidosKey(evento))).isFalse();
    }

    @Test
    void unReinicioDeRedisConservaLaPosicion() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        ingresar(evento, CLIENTE_A);
        ingresar(evento, CLIENTE_B);
        Posicion antes = consultar(evento, CLIENTE_B);

        try {
            REDIS.execInContainer("redis-cli", "DEBUG", "RESTART");
        } catch (Exception ex) {
            // El proceso se corta al reiniciar. El contenedor y el puerto publicado siguen.
        }
        conexiones.resetConnection();
        esperarRedis();

        Posicion despues = consultar(evento, CLIENTE_B);
        assertThat(despues.posicion()).isEqualTo(antes.posicion());
        assertThat(despues.estado()).isEqualTo("EN_ESPERA");
        assertThat(redis.opsForZSet().rank(FilaService.esperaKey(evento), CLIENTE_A)).isZero();
    }

    private void esperarRedis() {
        Instant limite = Instant.now().plus(Duration.ofSeconds(20));
        RuntimeException ultima = null;
        while (Instant.now().isBefore(limite)) {
            try {
                String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
                if ("PONG".equalsIgnoreCase(pong)) {
                    return;
                }
            } catch (RuntimeException ex) {
                ultima = ex;
                conexiones.resetConnection();
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        throw ultima == null ? new IllegalStateException("Redis no respondió al PING") : ultima;
    }

    private void abrir(UUID evento) {
        ResponseEntity<String> respuesta = rest.exchange(
                "/internal/events/" + evento,
                HttpMethod.POST,
                new HttpEntity<>(token()),
                String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Estado cerrar(UUID evento, String motivo) {
        ResponseEntity<Estado> respuesta = rest.exchange(
                "/internal/events/" + evento + "/close",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("motivo", motivo), token()),
                Estado.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        return respuesta.getBody();
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

    private static HttpHeaders token() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", "ticketya-internal-dev");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static HttpHeaders identidad(String cliente) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", cliente);
        headers.set("X-User-Role", "CLIENT");
        return headers;
    }

    private record Estado(String estado, String motivo) {
    }

    private record Posicion(String estado, int posicion, String token) {
    }
}
