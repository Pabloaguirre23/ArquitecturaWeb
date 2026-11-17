package ar.edu.unicen.ticketya.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifica que el esqueleto arranque con Redis real.
 * Se omite automáticamente si Docker no está corriendo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueServiceApplicationTests {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void elHealthReportaRedisConectado() {
        String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
        assertThat(pong).isEqualTo("PONG");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/actuator/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> health = response.getBody();
        assertThat(health).isNotNull();
        assertThat(health.get("status")).isEqualTo("UP");
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) health.get("components");
        assertThat(components).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> redisHealth = (Map<String, Object>) components.get("redis");
        assertThat(redisHealth).isNotNull();
        assertThat(redisHealth.get("status")).isEqualTo("UP");
    }
}
