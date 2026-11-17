package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compensación de la saga de compra (tarea 6.2).
 *
 * <p>Primero deja la orden y la saga en un estado reintentable, después ejecuta
 * los comandos idempotentes en orden inverso al avance: reintegro, devolución o
 * liberación en inventory. Si algo falla, la saga queda en COMPENSANDO y el
 * reintento programado vuelve a intentarlo.</p>
 */
@Service
public class OrderCompensationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationService.class);

    private final OrderPersistenceService orderPersistenceService;
    private final SagaCompraService sagaCompraService;
    private final ReintegroRepository reintegroRepository;
    private final PagoRepository pagoRepository;
    private final InventoryClient inventoryClient;
    private final PaymentGateway paymentGateway;
    private final Set<UUID> enProceso = ConcurrentHashMap.newKeySet();

    public OrderCompensationService(OrderPersistenceService orderPersistenceService,
                                    SagaCompraService sagaCompraService,
                                    ReintegroRepository reintegroRepository,
                                    PagoRepository pagoRepository,
                                    InventoryClient inventoryClient,
                                    PaymentGateway paymentGateway) {
        this.orderPersistenceService = orderPersistenceService;
        this.sagaCompraService = sagaCompraService;
        this.reintegroRepository = reintegroRepository;
        this.pagoRepository = pagoRepository;
        this.inventoryClient = inventoryClient;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Compensa una compra que no llegó a completarse.
     *
     * @param estadoOrden estado final de la orden, o {@code null} en un reintento
     *                    para conservar el que ya se persistió.
     * @param motivo      causa de la compensación; queda en {@code saga_compra.ultimo_error}.
     */
    public void compensar(UUID ordenId, EstadoOrden estadoOrden, String motivo) {
        if (!enProceso.add(ordenId)) {
            log.info("La orden {} ya tiene una compensación en curso en esta instancia", ordenId);
            return;
        }
        try {
            compensarInterno(ordenId, estadoOrden, motivo);
        } finally {
            enProceso.remove(ordenId);
        }
    }

    private void compensarInterno(UUID ordenId, EstadoOrden estadoOrden, String motivo) {
        boolean ventaConfirmada = sagaCompraService.iniciarCompensacion(ordenId, motivo);

        OrderPersistenceService.CompensacionPreparada preparada;
        try {
            preparada = orderPersistenceService.prepararCompensacion(ordenId, estadoOrden);
        } catch (IllegalStateException ex) {
            sagaCompraService.marcarFallida(ordenId, "No se pudo preparar la compensación: " + ex.getMessage());
            throw ex;
        }

        boolean inventarioOk = ventaConfirmada
                ? devolverItems(preparada)
                : liberarReserva(preparada, motivo);
        boolean reintegroOk = solicitarReintegro(preparada);

        if (inventarioOk && reintegroOk) {
            sagaCompraService.marcarCompensada(ordenId);
        } else {
            sagaCompraService.registrarError(ordenId, "Compensación incompleta (inventario="
                    + inventarioOk + ", reintegro=" + reintegroOk + ")");
        }
    }

    private boolean liberarReserva(OrderPersistenceService.CompensacionPreparada preparada, String motivo) {
        try {
            inventoryClient.liberarReserva(preparada.reservaId(), motivo,
                    ClavesIdempotencia.liberarReserva(preparada.ordenId()));
            return true;
        } catch (RuntimeException ex) {
            log.warn("No se pudo liberar la reserva {} de la orden {}: {}",
                    preparada.reservaId(), preparada.ordenId(), ex.getMessage());
            return false;
        }
    }

    private boolean devolverItems(OrderPersistenceService.CompensacionPreparada preparada) {
        UUID referencia = preparada.pagoId() == null ? preparada.ordenId() : preparada.pagoId();
        List<ReturnDto.Item> devoluciones = preparada.items().stream()
                .map(item -> new ReturnDto.Item(item.getSectorId(), item.getAsientoId(),
                        item.getAsientoId() == null ? item.getCantidad() : 1))
                .toList();
        try {
            inventoryClient.devolverItems(new ReturnDto(preparada.eventoId(), referencia,
                    devoluciones, preparada.clienteId()));
            return true;
        } catch (RuntimeException ex) {
            log.warn("No se pudieron devolver los ítems de la orden {}: {}",
                    preparada.ordenId(), ex.getMessage());
            return false;
        }
    }

    private boolean solicitarReintegro(OrderPersistenceService.CompensacionPreparada preparada) {
        if (preparada.pagoId() == null || preparada.reintegroAprobado()) {
            return true;
        }
        try {
            paymentGateway.reintegrar(preparada.pagoId(), preparada.monto(),
                    ClavesIdempotencia.reintegroCompra(preparada.pagoId()));
            if (preparada.reintegroId() != null) {
                reintegroRepository.findById(preparada.reintegroId()).ifPresent(reintegro -> {
                    reintegro.setEstado(EstadoReintegro.APROBADO);
                    reintegroRepository.save(reintegro);
                });
            }
            actualizarEstadoPago(preparada.pagoId());
            return true;
        } catch (RuntimeException ex) {
            log.warn("No se pudo reintegrar el pago {} de la orden {}: {}",
                    preparada.pagoId(), preparada.ordenId(), ex.getMessage());
            return false;
        }
    }

    private void actualizarEstadoPago(UUID pagoId) {
        Pago pago = pagoRepository.findById(pagoId).orElse(null);
        if (pago == null) {
            return;
        }
        List<Reintegro> reintegros = reintegroRepository.findByPagoId(pagoId);
        BigDecimal totalAprobado = reintegros.stream()
                .filter(r -> r.getEstado() == EstadoReintegro.APROBADO)
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hayPendientes = reintegros.stream().anyMatch(r -> r.getEstado() == EstadoReintegro.PENDIENTE);
        if (totalAprobado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.REINTEGRADO);
        } else if (hayPendientes) {
            pago.setEstado(EstadoPago.REINTEGRO_PENDIENTE);
        } else if (totalAprobado.compareTo(BigDecimal.ZERO) > 0) {
            pago.setEstado(EstadoPago.PARCIALMENTE_REINTEGRADO);
        }
        pagoRepository.save(pago);
    }
}
