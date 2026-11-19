package ar.edu.unicen.ticketya.recommendation.servicio;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.repositorio.EventoVistoRepository;
import ar.edu.unicen.ticketya.recommendation.repositorio.HistorialCompraRepository;
import ar.edu.unicen.ticketya.recommendation.web.dto.EventoSimilarItem;
import ar.edu.unicen.ticketya.recommendation.web.dto.EventosSimilaresResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recomendaciones colaborativas del tipo "quienes compraron este evento
 * también compraron". No usa ML: cuenta clientes distintos por evento.
 */
@Service
public class SimilarEventsService {

    private static final Logger log = LoggerFactory.getLogger(SimilarEventsService.class);
    private static final int LIMITE = 10;
    /** Cota de la consulta de co-compra para no armar un IN gigante. */
    private static final int MAX_COMPRADORES = 200;
    private static final Set<String> ESTADOS_ELEGIBLES = Set.of("PUBLICADO", "AGOTADO");

    private final HistorialCompraRepository historialRepository;
    private final EventoVistoRepository eventoVistoRepository;
    private final Clock clock;

    public SimilarEventsService(HistorialCompraRepository historialRepository,
                                EventoVistoRepository eventoVistoRepository,
                                Clock clock) {
        this.historialRepository = historialRepository;
        this.eventoVistoRepository = eventoVistoRepository;
        this.clock = clock;
    }

    public EventosSimilaresResponse similares(UUID clienteId, UUID eventoId) {
        Instant ahora = Instant.now(clock);
        String cliente = clienteId.toString();
        String objetivo = eventoId.toString();

        Set<String> compradosPorCliente = historialRepository.findByClienteId(cliente).stream()
                .map(HistorialCompra::getEventoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> otrosCompradores = historialRepository.findByEventoIdIn(List.of(objetivo)).stream()
                .map(HistorialCompra::getClienteId)
                .filter(id -> id != null && !cliente.equals(id))
                .collect(Collectors.toCollection(HashSet::new));
        if (otrosCompradores.isEmpty()) {
            return new EventosSimilaresResponse(objetivo, ahora, List.of());
        }

        // Clientes distintos por evento: una misma persona no suma dos veces aunque
        // haya comprado el evento en órdenes separadas.
        Map<String, Set<String>> compradoresPorEvento = new HashMap<>();
        List<String> muestraCompradores = otrosCompradores.stream().limit(MAX_COMPRADORES).toList();
        historialRepository.findByClienteIdIn(muestraCompradores).stream()
                .filter(compra -> compra.getEventoId() != null)
                .filter(compra -> compra.getClienteId() != null)
                .filter(compra -> !objetivo.equals(compra.getEventoId()))
                .filter(compra -> !compradosPorCliente.contains(compra.getEventoId()))
                .forEach(compra -> compradoresPorEvento
                        .computeIfAbsent(compra.getEventoId(), id -> new HashSet<>())
                        .add(compra.getClienteId()));
        if (compradoresPorEvento.isEmpty()) {
            return new EventosSimilaresResponse(objetivo, ahora, List.of());
        }

        Map<String, EventoVisto> snapshots = eventoVistoRepository
                .findAllById(compradoresPorEvento.keySet()).stream()
                .filter(evento -> evento.getEventoId() != null)
                .collect(Collectors.toMap(EventoVisto::getEventoId, Function.identity()));

        List<EventoSimilarItem> similares = compradoresPorEvento.entrySet().stream()
                .map(entrada -> item(entrada.getKey(), entrada.getValue().size(), snapshots, ahora))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(EventoSimilarItem::compradoresEnComun).reversed()
                        .thenComparing(EventoSimilarItem::fechaEvento,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EventoSimilarItem::nombre,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(LIMITE)
                .toList();

        log.debug("Eventos similares para {}: {} candidato(s) de {} comprador(es) en común",
                objetivo, similares.size(), otrosCompradores.size());
        return new EventosSimilaresResponse(objetivo, ahora, similares);
    }

    private EventoSimilarItem item(String eventoId, int compradoresEnComun,
                                   Map<String, EventoVisto> snapshots, Instant ahora) {
        EventoVisto evento = snapshots.get(eventoId);
        if (evento == null) {
            log.debug("Evento {} sin snapshot en eventos_vistos; se omite de similares", eventoId);
            return null;
        }
        if (!elegible(evento, ahora)) {
            return null;
        }
        return new EventoSimilarItem(
                evento.getEventoId(),
                evento.getNombre(),
                evento.getTipo(),
                evento.getArtistas() == null ? List.of() : evento.getArtistas(),
                evento.getCiudad(),
                evento.getFechaEvento(),
                compradoresEnComun);
    }

    private boolean elegible(EventoVisto evento, Instant ahora) {
        boolean estadoValido = evento.getEstado() != null
                && ESTADOS_ELEGIBLES.contains(evento.getEstado().toUpperCase(Locale.ROOT));
        boolean fechaFutura = evento.getFechaEvento() != null
                && evento.getFechaEvento().isAfter(ahora);
        return estadoValido && fechaFutura;
    }
}
