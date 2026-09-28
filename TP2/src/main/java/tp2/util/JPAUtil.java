package tp2.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton de EntityManagerFactory (una instancia por persistence-unit).
 * EMF es costoso de crear (metamodelo + pool); compartirlo es lo evaluado
 * junto a DTO en la corrección del TP1.
 *
 * Uso:
 *   EntityManager em = JPAUtil.getEntityManager("derbyPU");
 *   ...
 *   JPAUtil.close(); // al salir de Main
 */
public final class JPAUtil {

    private static final Map<String, EntityManagerFactory> FACTORIES = new ConcurrentHashMap<>();
    private static volatile String defaultUnit = "derbyPU";

    private JPAUtil() {}

    public static EntityManagerFactory getFactory() {
        return getFactory(defaultUnit);
    }

    public static EntityManagerFactory getFactory(String persistenceUnit) {
        EntityManagerFactory emf = FACTORIES.get(persistenceUnit);
        if (emf == null || !emf.isOpen()) {
            synchronized (JPAUtil.class) {
                emf = FACTORIES.get(persistenceUnit);
                if (emf == null || !emf.isOpen()) {
                    emf = Persistence.createEntityManagerFactory(persistenceUnit);
                    FACTORIES.put(persistenceUnit, emf);
                    defaultUnit = persistenceUnit;
                }
            }
        }
        return emf;
    }

    public static EntityManager getEntityManager(String persistenceUnit) {
        return getFactory(persistenceUnit).createEntityManager();
    }

    public static EntityManager getEntityManager() {
        return getEntityManager(defaultUnit);
    }

    /** Cierra todas las factories. Llamar una vez al final del programa. */
    public static synchronized void close() {
        for (EntityManagerFactory emf : FACTORIES.values()) {
            if (emf != null && emf.isOpen()) {
                emf.close();
            }
        }
        FACTORIES.clear();
    }

    // Compatibilidad con el Main inicial (delegan al singleton)
    public static EntityManagerFactory createFactory(String persistenceUnit) {
        return getFactory(persistenceUnit);
    }

    public static EntityManagerFactory createFactory() {
        return getFactory();
    }

    public static EntityManager createEntityManager(EntityManagerFactory emf) {
        return emf.createEntityManager();
    }
}
