package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.SagaCompraRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaCompraRecoveryServiceTest {

    @Mock private SagaCompraRepository sagaRepository;
    @Mock private OrdenRepository ordenRepository;
    @Mock private OrdenItemRepository ordenItemRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private InventoryClient inventoryClient;
    @Mock private OrderEmissionService orderEmissionService;
    @Mock private OrderCompensationService orderCompensationService;
    @Mock private SagaCompraService sagaCompraService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private SagaCompraRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new SagaCompraRecoveryService(sagaRepository, ordenRepository, ordenItemRepository,
                pagoRepository, inventoryClient, orderEmissionService, orderCompensationService,
                sagaCompraService, clock, Duration.ofMinutes(2));
    }

    @Test
    void reanudaUnaSagaConPagoAprobadoConfirmandoInventoryYEmite() {
        UUID ordenId = UUID.randomUUID();
        Orden orden = orden(ordenId, EstadoOrden.PENDIENTE_PAGO);
        Pago pago = pago(ordenId, EstadoPago.APROBADO);
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.PAGO_APROBADO);
        List<OrdenItem> items = List.of(new OrdenItem(ordenId, UUID.randomUUID(), null, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00")));
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of(pago));
        when(ordenItemRepository.findByOrdenId(ordenId)).thenReturn(items);

        service.reanudar(saga);

        verify(inventoryClient).confirmarReserva(eq(orden.getReservaId()), anyString());
        verify(sagaCompraService).pasoCompletado(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
        verify(orderEmissionService).marcarPagadaYEmitir(orden, items);
        verify(orderCompensationService, never()).compensar(any(), any(), anyString());
    }

    @Test
    void siInventoryYaEstabaConfirmadoSoloEmite() {
        UUID ordenId = UUID.randomUUID();
        Orden orden = orden(ordenId, EstadoOrden.PENDIENTE_PAGO);
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of(pago(ordenId, EstadoPago.APROBADO)));
        when(ordenItemRepository.findByOrdenId(ordenId)).thenReturn(List.of());

        service.reanudar(saga);

        verify(inventoryClient, never()).confirmarReserva(any(), anyString());
        verify(orderEmissionService).marcarPagadaYEmitir(eq(orden), any());
    }

    @Test
    void sinPagoAprobadoCompensaYLiberaLaReserva() {
        UUID ordenId = UUID.randomUUID();
        Orden orden = orden(ordenId, EstadoOrden.PENDIENTE_PAGO);
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.ORDEN_CREADA);
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of(pago(ordenId, EstadoPago.PENDIENTE)));

        service.reanudar(saga);

        verify(orderCompensationService).compensar(ordenId, EstadoOrden.ERROR_PAGO, "SAGA_INCOMPLETA");
        verify(orderEmissionService, never()).marcarPagadaYEmitir(any(), any());
    }

    @Test
    void unaOrdenYaPagadaSoloSeMarcaCompletada() {
        UUID ordenId = UUID.randomUUID();
        Orden orden = orden(ordenId, EstadoOrden.PAGADA);
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));

        service.reanudar(saga);

        verify(sagaCompraService).marcarCompletada(ordenId);
        verify(orderEmissionService, never()).marcarPagadaYEmitir(any(), any());
    }

    private Orden orden(UUID ordenId, EstadoOrden estado) {
        Orden orden = new Orden();
        orden.setId(ordenId);
        orden.setClienteId(UUID.randomUUID());
        orden.setReservaId(UUID.randomUUID());
        orden.setEventoId(UUID.randomUUID());
        orden.setEstado(estado);
        return orden;
    }

    private Pago pago(UUID ordenId, EstadoPago estado) {
        Pago pago = new Pago();
        pago.setId(UUID.randomUUID());
        pago.setOrdenId(ordenId);
        pago.setEstado(estado);
        pago.setMonto(new BigDecimal("1000.00"));
        return pago;
    }
}
