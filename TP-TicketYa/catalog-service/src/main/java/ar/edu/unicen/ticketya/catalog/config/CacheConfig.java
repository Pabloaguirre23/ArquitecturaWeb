package ar.edu.unicen.ticketya.catalog.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String VENUES = "venues";
    public static final String VENUE = "venue";
    public static final String VENUE_SECTORS = "venue-sectors";
    public static final String EVENT_DETAIL_STATIC = "event-detail-static";
    public static final String EVENT_SEARCH = "event-search";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          @Value("${catalog.cache.venue-ttl:10m}") Duration venueTtl,
                                          @Value("${catalog.cache.event-detail-ttl:2m}") Duration detailTtl,
                                          @Value("${catalog.cache.search-ttl:30s}") Duration searchTtl) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer()));
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(VENUES, base.entryTtl(venueTtl));
        configs.put(VENUE, base.entryTtl(venueTtl));
        configs.put(VENUE_SECTORS, base.entryTtl(venueTtl));
        configs.put(EVENT_DETAIL_STATIC, base.entryTtl(detailTtl));
        configs.put(EVENT_SEARCH, base.entryTtl(searchTtl));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(searchTtl))
                .withInitialCacheConfigurations(configs)
                .build();
    }

    /**
     * Jackson con JSR-310 y typing por propiedad. Sin el módulo de fechas,
     * cualquier DTO con OffsetDateTime/Instant fallaba al escribir en Redis y
     * el CacheErrorHandler lo tragaba: la caché nunca persistía.
     */
    private static GenericJackson2JsonRedisSerializer serializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private final Logger log = LoggerFactory.getLogger(CacheErrorHandler.class);

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET degradado cache={} key={}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT degradado cache={} key={}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT degradado cache={} key={}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR degradado cache={}: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
