package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoSaga;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.SagaCompraRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Forward recovery de la saga de compra (revisión de la Entrega 2).
 *
 * <p>Si el proceso muere entre el pago aprobado y la emisión, la orden queda
 * PENDIENTE_PAGO y un reintento del cliente devuelve 410 porque la reserva ya
 * está confirmada. Este job toma las sagas EN_PROCESO que dejaron de avanzar y
 * las reanuda desde el último paso persistido:</p>
 *
 * <ul>
 *   <li>PAGO_APROBADO pendiente de confirmar: confirma inventory (idempotente) y emite.</li>
 *   <li>INVENTARIO_CONFIRMADO pendiente de emitir: emite las entradas.</li>
 *   <li>Sin pago aprobado: compensa y libera la reserva.</li>
 * </ul>
 *
 * <p>Todos los comandos usan las claves de idempotencia de la saga, así que
 * reanudar dos veces no duplica cobros, lugares ni entradas.</p>
 */
@Service
public class SagaCompraRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompraRecoveryService.class);

    private final SagaCompraRepository sagaRepository;
    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final PagoRepository pagoRepository;
    private final InventoryClient inventoryClient;
    private final OrderEmissionService orderEmissionService;
    private final OrderCompensationService orderCompensationService;
    private final SagaCompraService sagaCompraService;
    private final Clock clock;
    private final Duration demoraMinima;
    private final Set<UUID> enProceso = ConcurrentHashMap.newKeySet();

    public SagaCompraRecoveryService(SagaCompraRepository sagaRepository,
                                     OrdenRepository ordenRepository,
                                     OrdenItemRepository ordenItemRepository,
                                     PagoRepository pagoRepository,
                                     InventoryClient inventoryClient,
                                     OrderEmissionService orderEmissionService,
                                     OrderCompensationService orderCompensationService,
                                     SagaCompraService sagaCompraService,
                                     Clock clock,
                                     @Value("${orders.sagas.recovery-delay:2m}") Duration demoraMinima) {
        this.sagaRepository = sagaRepository;
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.pagoRepository = pagoRepository;
        this.inventoryClient = inventoryClient;
        this.orderEmissionService = orderEmissionService;
        this.orderCompensationService = orderCompensationService;
        this.sagaCompraService = sagaCompraService;
        this.clock = clock;
        this.demoraMinima = demoraMinima;
    }

    @Scheduled(fixedDelayString = "${orders.sagas.recovery-ms:60000}")
    public void recuperarSagasTrabadas() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minus(demoraMinima);
        List<SagaCompra> trabadas = sagaRepository.findByEstadoAndUpdatedAtBefore(
                EstadoSaga.EN_PROCESO, limite);
        for (SagaCompra saga : trabadas) {
            if (!enProceso.add(saga.getOrdenId())) {
                continue;
            }
            try {
                reanudar(saga);
            } catch (RuntimeException ex) {
                log.warn("No se pudo reanudar la saga de la orden {}: {}",
                        saga.getOrdenId(), ex.getMessage());
            } finally {
                enProceso.remove(saga.getOrdenId());
            }
        }
    }

    /**
     * Reanuda una saga desde su último paso persistido. Es pública para poder
     * invocarla desde un test o un endpoint operativo sin esperar al scheduler.
     */
    public void reanudar(SagaCompra saga) {
        UUID ordenId = saga.getOrdenId();
        Orden orden = ordenRepository.findById(ordenId).orElse(null);
        if (orden == null) {
            sagaCompraService.marcarFallida(ordenId, "La orden de la saga ya no existe");
            return;
        }
        if (orden.getEstado() == EstadoOrden.PAGADA || saga.getPaso() == PasoSaga.ENTRADAS_EMITIDAS) {
            sagaCompraService.marcarCompletada(ordenId);
            return;
        }

        Pago pago = pagoAprobado(ordenId);
        if (pago == null) {
            // Nunca se registró un cobro aprobado: se libera la reserva. Si el
            // pago quedó PENDIENTE porque el proceso murió justo después de
            // cobrar, la pasarela real debe consultarse por idempotency key;
            // el mock de esta entrega es síncrono y no tiene consulta.
            log.info("Saga {} sin pago aprobado: se compensa y libera la reserva", ordenId);
            orderCompensationService.compensar(ordenId, EstadoOrden.ERROR_PAGO, "SAGA_INCOMPLETA");
            return;
        }

        try {
            if (saga.getPaso().ordinal() < PasoSaga.INVENTARIO_CONFIRMADO.ordinal()) {
                inventoryClient.confirmarReserva(orden.getReservaId(),
                        ClavesIdempotencia.paso(ordenId, PasoSaga.INVENTARIO_CONFIRMADO));
                sagaCompraService.pasoCompletado(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
            }
            List<OrdenItem> items = ordenItemRepository.findByOrdenId(ordenId);
            orderEmissionService.marcarPagadaYEmitir(orden, items);
            log.info("Saga {} reanudada: pago aprobado y entradas emitidas", ordenId);
        } catch (RuntimeException ex) {
            log.warn("Falló la reanudación de la orden {}; se compensa: {}", ordenId, ex.getMessage());
            orderCompensationService.compensar(ordenId, EstadoOrden.REINTEGRO_PENDIENTE,
                    "ERROR_RECUPERACION");
        }
    }

    private Pago pagoAprobado(UUID ordenId) {
        return pagoRepository.findByOrdenId(ordenId).stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO)
                .findFirst()
                .orElse(null);
    }
}
