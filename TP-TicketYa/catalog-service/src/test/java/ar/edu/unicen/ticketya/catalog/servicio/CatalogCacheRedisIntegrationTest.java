package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.catalog.config.CacheConfig;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CatalogCacheRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void elTtlRealDeRedisExpiraLaEntradaYVuelveAMiss() throws Exception {
        LettuceConnectionFactory conexion = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        conexion.afterPropertiesSet();
        RedisCacheManager manager = new CacheConfig().cacheManager(conexion,
                Duration.ofMinutes(10), Duration.ofMinutes(2), Duration.ofSeconds(1));
        manager.afterPropertiesSet();
        CatalogCacheService cacheService = new CatalogCacheService(manager);
        AtomicInteger cargas = new AtomicInteger();

        assertThat(cacheService.getOrLoad(CacheConfig.EVENT_SEARCH, "clave-ttl",
                () -> {
                    cargas.incrementAndGet();
                    return "base";
                }).header()).isEqualTo("MISS");
        assertThat(cacheService.getOrLoad(CacheConfig.EVENT_SEARCH, "clave-ttl",
                () -> {
                    cargas.incrementAndGet();
                    return "base";
                }).header()).isEqualTo("HIT");
        assertThat(cargas.get()).isEqualTo(1);

        Thread.sleep(1500);

        assertThat(cacheService.getOrLoad(CacheConfig.EVENT_SEARCH, "clave-ttl",
                () -> {
                    cargas.incrementAndGet();
                    return "base";
                }).header()).isEqualTo("MISS");
        assertThat(cargas.get()).isEqualTo(2);

        conexion.destroy();
    }
}
