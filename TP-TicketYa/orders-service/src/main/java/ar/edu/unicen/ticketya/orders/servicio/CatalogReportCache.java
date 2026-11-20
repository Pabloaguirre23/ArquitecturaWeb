package ar.edu.unicen.ticketya.orders.servicio;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Cache en memoria con TTL (60 s) de la última respuesta buena de catalog.
 * Solo se usa para degradar el reporte cuando catalog no responde (tarea 6.5).
 */
@Component
public class CatalogReportCache {

    private static final long TTL_SEGUNDOS = 60;

    private final ConcurrentHashMap<UUID, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock clock;

    public CatalogReportCache(Clock clock) {
        this.clock = clock;
    }

    public void guardar(UUID eventoId, CatalogReport reporte) {
        if (eventoId == null || reporte == null) {
            return;
        }
        entradas.put(eventoId, new Entrada(reporte, clock.instant().getEpochSecond() + TTL_SEGUNDOS));
    }

    /** Devuelve la copia vigente, o null si no hay o expiró. */
    public CatalogReport obtener(UUID eventoId) {
        Entrada entrada = entradas.get(eventoId);
        if (entrada == null) {
            return null;
        }
        if (entrada.expiraEn() <= clock.instant().getEpochSecond()) {
            entradas.remove(eventoId, entrada);
            return null;
        }
        return entrada.reporte();
    }

    private record Entrada(CatalogReport reporte, long expiraEn) {
    }
}
