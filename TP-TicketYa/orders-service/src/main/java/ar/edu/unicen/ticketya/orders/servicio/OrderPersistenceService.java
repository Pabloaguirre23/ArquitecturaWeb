package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste la orden, sus ítems, el pago y el avance de la saga en transacciones
 * cortas que se confirman antes de llamar a servicios externos. Así un fallo
 * posterior no borra el registro del cobro ni el último paso completado.
 */
@Service
public class OrderPersistenceService {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;
    private final SagaCompraService sagaCompraService;

    public OrderPersistenceService(OrdenRepository ordenRepository,
                                   OrdenItemRepository ordenItemRepository,
                                   PagoRepository pagoRepository,
                                   ReintegroRepository reintegroRepository,
                                   SagaCompraService sagaCompraService) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
        this.sagaCompraService = sagaCompraService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrdenCreada crearPendiente(ReservationDto reserva, UUID clienteId, CrearOrdenRequest request) {
        BigDecimal total = reserva.items().stream()
                .map(ReservationDto.Item::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Orden orden = new Orden();
        orden.setClienteId(clienteId);
        orden.setReservaId(reserva.id());
        orden.setEventoId(reserva.eventoId());
        orden.setEventoNombre(reserva.eventoNombre());
        orden.setEventoFecha(reserva.eventoFecha());
        orden.setEstado(EstadoOrden.PENDIENTE_PAGO);
        orden.setMedioPago(request.medioPago());
        orden.setTotal(total);
        orden = ordenRepository.save(orden);

        List<OrdenItem> items = new ArrayList<>();
        for (ReservationDto.Item item : reserva.items()) {
            items.add(new OrdenItem(orden.getId(), item.sectorId(), item.asientoId(),
                    item.cantidad(), item.precioUnitario(), item.subtotal()));
        }
        items = ordenItemRepository.saveAll(items);

        Pago pago = new Pago();
        pago.setOrdenId(orden.getId());
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setMonto(total);
        pago.setMedioPago(request.medioPago());
        pago.setToken(request.token());
        pago = pagoRepository.save(pago);

        // La reserva ya fue validada en el paso anterior; la saga nace con la orden.
        sagaCompraService.iniciar(orden.getId());

        return new OrdenCreada(orden, items, pago);
    }

    /**
     * Registra el pago aprobado y avanza la saga en la misma transacción, para
     * que un reinicio no pierda el cobro ni deje el paso atrás.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarPagoAprobado(UUID ordenId, UUID pagoId, String externalId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalStateException("No existe el pago " + pagoId));
        pago.setEstado(EstadoPago.APROBADO);
        pago.setExternalId(externalId);
        pagoRepository.save(pago);
        sagaCompraService.pasoCompletadoEnTransaccion(ordenId, PasoSaga.PAGO_APROBADO);
    }

    /**
     * Registra un pago que no se aprobó. La liberación de la reserva y el estado
     * final de la orden los completa {@link OrderCompensationService}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarPagoRechazado(UUID ordenId, UUID pagoId, EstadoOrden estadoOrden) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalStateException("No existe la orden " + ordenId));
        orden.setEstado(estadoOrden);
        ordenRepository.save(orden);

        pagoRepository.findById(pagoId).ifPresent(pago -> {
            pago.setEstado(EstadoPago.RECHAZADO);
            pagoRepository.save(pago);
        });
    }

    /**
     * Prepara la compensación en una transacción propia: deja la orden en el
     * estado final, crea el reintegro pendiente si el pago estaba aprobado y
     * devuelve una foto de los datos que necesitan los comandos externos.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompensacionPreparada prepararCompensacion(UUID ordenId, EstadoOrden estadoOrden) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalStateException("No existe la orden " + ordenId));
        if (estadoOrden != null) {
            orden.setEstado(estadoOrden);
            ordenRepository.save(orden);
        }

        Pago pago = pagoRepository.findByOrdenId(ordenId).stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO
                        || p.getEstado() == EstadoPago.REINTEGRO_PENDIENTE
                        || p.getEstado() == EstadoPago.PARCIALMENTE_REINTEGRADO
                        || p.getEstado() == EstadoPago.REINTEGRADO)
                .findFirst()
                .orElse(null);

        UUID reintegroId = null;
        boolean reintegroAprobado = false;
        if (pago != null) {
            reintegroAprobado = pago.getEstado() == EstadoPago.REINTEGRADO;
            if (!reintegroAprobado) {
                Reintegro reintegro = reintegroRepository.findByPagoId(pago.getId()).stream()
                        .filter(r -> r.getEntradaId() == null)
                        .findFirst()
                        .orElseGet(() -> reintegroRepository.save(new Reintegro(
                                pago.getId(), null, pago.getMonto(), EstadoReintegro.PENDIENTE)));
                reintegroId = reintegro.getId();
                reintegroAprobado = reintegro.getEstado() == EstadoReintegro.APROBADO;
            }
        }

        return new CompensacionPreparada(ordenId, orden.getReservaId(), orden.getEventoId(),
                orden.getClienteId(), pago == null ? null : pago.getId(),
                pago == null ? null : pago.getMonto(), reintegroId, reintegroAprobado,
                ordenItemRepository.findByOrdenId(ordenId));
    }

    public record OrdenCreada(Orden orden, List<OrdenItem> items, Pago pago) {
    }

    public record CompensacionPreparada(
            UUID ordenId,
            UUID reservaId,
            UUID eventoId,
            UUID clienteId,
            UUID pagoId,
            BigDecimal monto,
            UUID reintegroId,
            boolean reintegroAprobado,
            List<OrdenItem> items) {
    }
}
