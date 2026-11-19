package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reconciliación periódica del estado AGOTADO/PUBLICADO hacia catalog y, si el evento
 * de alta demanda sigue agotado, del cierre de la fila.
 *
 * <p>El aviso normal es fire-and-forget: si catalog o queue están caídos, el estado puede
 * quedar desfasado. Este job reenvía el estado local actual, así el próximo tick corrige
 * el fallo parcial sin reabrir una fila que ya se cerró.</p>
 */
@Service
public class DisponibilidadReconciliador {

    private static final Logger log = LoggerFactory.getLogger(DisponibilidadReconciliador.class);

    private final EventoInventarioRepository eventoRepository;
    private final CatalogClient catalogClient;
    private final QueueFilaClient queueFilaClient;

    public DisponibilidadReconciliador(EventoInventarioRepository eventoRepository,
                                       CatalogClient catalogClient,
                                       QueueFilaClient queueFilaClient) {
        this.eventoRepository = eventoRepository;
        this.catalogClient = catalogClient;
        this.queueFilaClient = queueFilaClient;
    }

    @Scheduled(fixedDelayString = "${inventory.disponibilidad.reconciliacion-ms:300000}")
    public void reconciliar() {
        List<EventoInventario> eventos = eventoRepository.findByEstadoIn(
                List.of(EstadoEventoInventario.PUBLICADO, EstadoEventoInventario.AGOTADO));
        for (EventoInventario evento : eventos) {
            boolean agotado = evento.getEstado() == EstadoEventoInventario.AGOTADO;
            log.debug("Reconciliando disponibilidad de {} ({})", evento.getEventoId(), evento.getEstado());
            catalogClient.notificarDisponibilidad(evento.getEventoId(), agotado);
            if (agotado && evento.isAltaDemanda()) {
                queueFilaClient.cerrarAgotada(evento.getEventoId());
            }
        }
    }
}
