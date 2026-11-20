package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.catalog.config.CacheConfig;
import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    @Test
    void persisteUnDtoConOffsetDateTimeYDevuelveHit() {
        LettuceConnectionFactory conexion = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        conexion.afterPropertiesSet();
        RedisCacheManager manager = new CacheConfig().cacheManager(conexion,
                Duration.ofMinutes(10), Duration.ofMinutes(2), Duration.ofSeconds(30));
        manager.afterPropertiesSet();
        CatalogCacheService cacheService = new CatalogCacheService(manager);
        Muestra muestra = new Muestra("Festival", OffsetDateTime.parse("2027-03-14T21:00:00-03:00"));
        AtomicInteger cargas = new AtomicInteger();

        assertThat(cacheService.getOrLoad(CacheConfig.EVENT_DETAIL_STATIC, "evento-con-fecha",
                () -> {
                    cargas.incrementAndGet();
                    return muestra;
                }).header()).isEqualTo("MISS");

        CatalogCacheService.CacheResult<Muestra> segundo = cacheService.getOrLoad(
                CacheConfig.EVENT_DETAIL_STATIC, "evento-con-fecha",
                () -> {
                    cargas.incrementAndGet();
                    return new Muestra("otro", OffsetDateTime.now());
                });

        assertThat(segundo.header()).isEqualTo("HIT");
        assertThat(segundo.value()).isEqualTo(muestra);
        assertThat(cargas.get()).isEqualTo(1);

        conexion.destroy();
    }

    /**
     * Regresión del serializador de Redis: el DTO real que cachea el detalle
     * estático (UUID, enums, BigDecimal, OffsetDateTime y listas inmutables)
     * debe sobrevivir el round-trip; si falla, la caché nunca hace HIT.
     */
    @Test
    void persisteElDtoRealDelDetalleEstaticoYDevuelveHit() {
        LettuceConnectionFactory conexion = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        conexion.afterPropertiesSet();
        RedisCacheManager manager = new CacheConfig().cacheManager(conexion,
                Duration.ofMinutes(10), Duration.ofMinutes(2), Duration.ofSeconds(30));
        manager.afterPropertiesSet();
        CatalogCacheService cacheService = new CatalogCacheService(manager);
        EventoDetalleEstatico dto = new EventoDetalleEstatico(
                UUID.randomUUID(), "Festival de Rock", "Escenario de prueba", TipoEvento.FESTIVAL,
                UUID.randomUUID(), "Estadio Municipal", "Tandil", UUID.randomUUID(),
                OffsetDateTime.parse("2027-03-14T21:00:00-03:00"), EstadoEvento.PUBLICADO,
                4, true,
                List.of("Los Piojos", "Callejeros"),
                new ArrayList<>(List.of(new EventoDetalleEstatico.SectorEstatico(
                        UUID.randomUUID(), "Platea", TipoSector.NUMERADO,
                        new BigDecimal("25000.00"), 100))));
        AtomicInteger cargas = new AtomicInteger();

        assertThat(cacheService.getOrLoad(CacheConfig.EVENT_DETAIL_STATIC, "dto-real",
                () -> {
                    cargas.incrementAndGet();
                    return dto;
                }).header()).isEqualTo("MISS");

        CatalogCacheService.CacheResult<EventoDetalleEstatico> segundo = cacheService.getOrLoad(
                CacheConfig.EVENT_DETAIL_STATIC, "dto-real",
                () -> {
                    cargas.incrementAndGet();
                    return null;
                });

        assertThat(segundo.header()).isEqualTo("HIT");
        assertThat(segundo.value()).isEqualTo(dto);
        assertThat(cargas.get()).isEqualTo(1);

        conexion.destroy();
    }

    record Muestra(String nombre, OffsetDateTime fecha) {
    }
}
