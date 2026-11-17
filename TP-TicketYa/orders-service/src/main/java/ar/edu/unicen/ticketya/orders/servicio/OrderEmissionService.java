package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.eventos.OrderDomainEvents;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite las entradas en una transacción propia y solo entonces deja la orden
 * PAGADA. La emisión es idempotente: si la orden ya tiene entradas, no se
 * duplican. Un fallo acá revierte también el estado PAGADA, así nunca queda una
 * orden pagada sin entradas válidas (tarea 6.2).
 */
@Service
public class OrderEmissionService {

    private final OrdenRepository ordenRepository;
    private final TicketService ticketService;
    private final SagaCompraService sagaCompraService;
    private final OutboxService outboxService;

    public OrderEmissionService(OrdenRepository ordenRepository, TicketService ticketService,
                                SagaCompraService sagaCompraService, OutboxService outboxService) {
        this.ordenRepository = ordenRepository;
        this.ticketService = ticketService;
        this.sagaCompraService = sagaCompraService;
        this.outboxService = outboxService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Entrada> marcarPagadaYEmitir(Orden orden, List<OrdenItem> items) {
        List<Entrada> entradas = ticketService.emitir(orden, items);
        if (entradas == null || entradas.isEmpty()) {
            throw new IllegalStateException("La orden no puede quedar PAGADA sin entradas");
        }
        orden.setEstado(EstadoOrden.PAGADA);
        ordenRepository.save(orden);
        // Mismo commit que el estado PAGADA: no existe orden pagada sin su evento.
        outboxService.registrar(OrderDomainEvents.ORDEN_PAGADA, orden.getId(),
                OrderDomainEvents.ordenPagada(orden));
        sagaCompraService.completarEnTransaccion(orden.getId());
        return entradas;
    }
}
