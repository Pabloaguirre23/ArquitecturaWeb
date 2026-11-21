package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.config.CacheConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CatalogCacheService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);
    private static final Object CACHE_ERROR = new Object();

    private final CacheManager cacheManager;
    private final ConcurrentHashMap<String, LoadLock> loadLocks = new ConcurrentHashMap<>();

    public CatalogCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> getOrLoad(String cacheName, Object key, Supplier<T> loader) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return new CacheResult<>(loader.get(), "BYPASS");
        }
        Object cached = cachedValue(cache, cacheName, key);
        if (cached == CACHE_ERROR) {
            return new CacheResult<>(loader.get(), "BYPASS");
        }
        if (cached != null) {
            return new CacheResult<>((T) cached, "HIT");
        }

        String lockKey = cacheName + ":" + key;
        LoadLock lock = acquire(lockKey);
        try {
            lock.lock.lock();
            try {
                cached = cachedValue(cache, cacheName, key);
                if (cached == CACHE_ERROR) {
                    return new CacheResult<>(loader.get(), "BYPASS");
                }
                if (cached != null) {
                    return new CacheResult<>((T) cached, "HIT");
                }
                T loaded = loader.get();
                try {
                    cache.put(key, loaded);
                } catch (RuntimeException ex) {
                    log.warn("Cache PUT degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
                }
                return new CacheResult<>(loaded, "MISS");
            } finally {
                lock.lock.unlock();
            }
        } finally {
            release(lockKey, lock);
        }
    }

    private Object cachedValue(Cache cache, String cacheName, Object key) {
        try {
            Cache.ValueWrapper wrapper = cache.get(key);
            return wrapper == null ? null : wrapper.get();
        } catch (RuntimeException ex) {
            log.warn("Cache GET degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
            return CACHE_ERROR;
        }
    }

    private LoadLock acquire(String lockKey) {
        return loadLocks.compute(lockKey, (key, actual) -> {
            LoadLock lock = actual == null ? new LoadLock() : actual;
            lock.users++;
            return lock;
        });
    }

    private void release(String lockKey, LoadLock lock) {
        loadLocks.computeIfPresent(lockKey, (key, actual) -> {
            actual.users--;
            return actual.users == 0 ? null : actual;
        });
    }

    private static final class LoadLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    /**
     * Invalida una entrada. Si hay una transacción de escritura activa, la
     * invalidación se difiere al commit: un lector que re-cacheara entre el
     * evict y el commit guardaría el valor viejo que la transacción todavía
     * no hizo visible.
     */
    public void evict(String cacheName, Object key) {
        if (enTransaccion()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAhora(cacheName, key);
                }
            });
            return;
        }
        evictAhora(cacheName, key);
    }

    private void evictAhora(String cacheName, Object key) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException ex) {
            log.warn("Cache EVICT degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
        }
    }

    /** Limpia la caché completa, también difiriendo al commit si hay transacción. */
    public void clear(String cacheName) {
        if (enTransaccion()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearAhora(cacheName);
                }
            });
            return;
        }
        clearAhora(cacheName);
    }

    private void clearAhora(String cacheName) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
        } catch (RuntimeException ex) {
            log.warn("Cache CLEAR degradado cache={}: {}", cacheName, ex.getMessage());
        }
    }

    private boolean enTransaccion() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    public void clearSearches() {
        clear(CacheConfig.EVENT_SEARCH);
    }

    public void evictEventCaches(java.util.UUID eventoId) {
        evict(CacheConfig.EVENT_DETAIL_STATIC, eventoId);
        clearSearches();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }

    public record CacheResult<T>(T value, String header) {}
}