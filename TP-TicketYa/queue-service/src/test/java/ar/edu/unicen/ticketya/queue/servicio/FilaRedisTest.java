package ar.edu.unicen.ticketya.queue.servicio;

import static org.assertj.core.api.Assertions.assertThat;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilaRedisTest {

    private static final String CLIENTE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CLIENTE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

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

    @BeforeEach
    void limpiar() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void primerIngresoAsignaPosicionYElReingresoNoDuplica() {
        UUID evento = UUID.randomUUID();
        abrir(evento);

        ResponseEntity<Posicion> primero = ingresar(evento, CLIENTE_A);
        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(primero.getBody()).isNotNull();
        assertThat(primero.getBody().posicion()).isEqualTo(1);
        assertThat(primero.getBody().adelante()).isZero();
        assertThat(primero.getBody().estado()).isEqualTo("EN_ESPERA");
        assertThat(primero.getBody().estimacionSegundos()).isEqualTo(30);

        ResponseEntity<Posicion> segundo = ingresar(evento, CLIENTE_B);
        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(segundo.getBody().posicion()).isEqualTo(2);
        assertThat(segundo.getBody().adelante()).isEqualTo(1);
        assertThat(segundo.getBody().estimacionSegundos()).isEqualTo(30);

        ResponseEntity<Posicion> reingreso = ingresar(evento, CLIENTE_A);
        assertThat(reingreso.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reingreso.getBody().posicion()).isEqualTo(1);
        assertThat(redis.opsForZSet().size(FilaService.esperaKey(evento))).isEqualTo(2);

        ResponseEntity<Posicion> consulta = rest.exchange(
                "/api/queue/events/" + evento + "/position",
                HttpMethod.GET,
                new HttpEntity<>(identidad(CLIENTE_B)),
                Posicion.class);
        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consulta.getBody().posicion()).isEqualTo(2);
        assertThat(consulta.getBody().adelante()).isEqualTo(1);
    }

    @Test
    void ingresoSinFilaResponde409YQuienNoIngresoRecibe404() {
        UUID evento = UUID.randomUUID();

        ResponseEntity<String> sinFila = rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(identidad(CLIENTE_A)),
                String.class);
        assertThat(sinFila.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sinFila.getBody()).contains("QUEUE_NOT_OPEN");
        assertThat(redis.hasKey(FilaService.esperaKey(evento))).isFalse();

        abrir(evento);
        ResponseEntity<String> ausente = rest.exchange(
                "/api/queue/events/" + evento + "/position",
                HttpMethod.GET,
                new HttpEntity<>(identidad(CLIENTE_A)),
                String.class);
        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ausente.getBody()).contains("NOT_IN_QUEUE");
    }

    @Test
    void unOrganizadorNoEntraNiConsulta() {
        UUID evento = UUID.randomUUID();
        abrir(evento);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", CLIENTE_A);
        headers.set("X-User-Role", "ORGANIZER");

        ResponseEntity<String> ingreso = rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
        ResponseEntity<String> posicion = rest.exchange(
                "/api/queue/events/" + evento + "/position",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(ingreso.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ingreso.getBody()).contains("FORBIDDEN");
        assertThat(posicion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(redis.opsForZSet().size(FilaService.esperaKey(evento))).isZero();
    }

    @Test
    void sinIdentidadResponde401() {
        UUID evento = UUID.randomUUID();
        ResponseEntity<String> respuesta = rest.postForEntity("/api/queue/events/" + evento + "/join", null, String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody()).contains("UNAUTHORIZED");
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

    private ResponseEntity<Posicion> ingresar(UUID evento, String cliente) {
        return rest.exchange(
                "/api/queue/events/" + evento + "/join",
                HttpMethod.POST,
                new HttpEntity<>(identidad(cliente)),
                Posicion.class);
    }

    private static HttpHeaders identidad(String cliente) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", cliente);
        headers.set("X-User-Role", "CLIENT");
        return headers;
    }

    private record Posicion(String eventoId, int posicion, int adelante, String estado, int estimacionSegundos) {
    }
}
