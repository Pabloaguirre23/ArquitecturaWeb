package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCompensationServiceTest {

    @Mock
    private OrderPersistenceService orderPersistenceService;
    @Mock
    private SagaCompraService sagaCompraService;
    @Mock
    private ReintegroRepository reintegroRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private PaymentGateway paymentGateway;

    private OrderCompensationService service;

    private final UUID ordenId = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();
    private final UUID eventoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID pagoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderCompensationService(orderPersistenceService, sagaCompraService,
                reintegroRepository, pagoRepository, inventoryClient, paymentGateway);
    }

    @Test
    void sinInventarioConfirmadoLiberaLaReservaYNoDevuelveItems() {
        when(sagaCompraService.iniciarCompensacion(ordenId, "PAGO_RECHAZADO")).thenReturn(false);
        when(orderPersistenceService.prepararCompensacion(ordenId, EstadoOrden.RECHAZADA))
                .thenReturn(preparada(null, null, null, null, List.of()));

        service.compensar(ordenId, EstadoOrden.RECHAZADA, "PAGO_RECHAZADO");

        verify(inventoryClient).liberarReserva(reservaId, "PAGO_RECHAZADO",
                ClavesIdempotencia.liberarReserva(ordenId));
        verify(inventoryClient, never()).devolverItems(any());
        verify(paymentGateway, never()).reintegrar(any(), any(), anyString());
        verify(sagaCompraService).marcarCompensada(ordenId);
    }

    @Test
    void conInventarioConfirmadoDevuelveItemsYReintegraElPagoUnaSolaVez() {
        UUID reintegroId = UUID.randomUUID();
        Reintegro reintegro = new Reintegro(pagoId, null, new BigDecimal("15000.00"),
                EstadoReintegro.PENDIENTE);
        reintegro.setId(reintegroId);
        Pago pago = pago(EstadoPago.APROBADO);
        OrdenItem item = new OrdenItem(ordenId, UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("15000.00"), new BigDecimal("15000.00"));
        OrderPersistenceService.CompensacionPreparada preparada = preparada(pagoId,
                new BigDecimal("15000.00"), reintegroId, false, List.of(item));

        when(sagaCompraService.iniciarCompensacion(ordenId, "ERROR_EMISION")).thenReturn(true);
        when(orderPersistenceService.prepararCompensacion(ordenId, EstadoOrden.REINTEGRO_PENDIENTE))
                .thenReturn(preparada);
        when(reintegroRepository.findById(reintegroId)).thenReturn(Optional.of(reintegro));
        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pago));
        when(reintegroRepository.findByPagoId(pagoId)).thenReturn(List.of(reintegro));
        when(reintegroRepository.save(any(Reintegro.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.compensar(ordenId, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_EMISION");

        ArgumentCaptor<ReturnDto> devolucion = ArgumentCaptor.forClass(ReturnDto.class);
        verify(inventoryClient).devolverItems(devolucion.capture());
        assertThat(devolucion.getValue().referencia()).isEqualTo(pagoId);
        assertThat(devolucion.getValue().clienteId()).isEqualTo(clienteId);
        verify(paymentGateway).reintegrar(pagoId, new BigDecimal("15000.00"),
                ClavesIdempotencia.reintegroCompra(pagoId));
        verify(inventoryClient, never()).liberarReserva(any(), anyString(), anyString());
        verify(sagaCompraService).marcarCompensada(ordenId);
    }

    @Test
    void siInventoryFallaLaSagaQuedaEnCompensandoParaReintentar() {
        when(sagaCompraService.iniciarCompensacion(ordenId, "ERROR_CONFIRMACION")).thenReturn(false);
        when(orderPersistenceService.prepararCompensacion(ordenId, EstadoOrden.REINTEGRO_PENDIENTE))
                .thenReturn(preparada(null, null, null, null, List.of()));
        doThrow(new RuntimeException("inventory caído"))
                .when(inventoryClient).liberarReserva(eq(reservaId), anyString(), anyString());

        service.compensar(ordenId, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_CONFIRMACION");

        verify(sagaCompraService, never()).marcarCompensada(ordenId);
        verify(sagaCompraService).registrarError(eq(ordenId), contains("inventario=false"));
    }

    @Test
    void siLaPasarelaFallaElReintegroQuedaPendienteYLaSagaSeReintenta() {
        UUID reintegroId = UUID.randomUUID();
        when(sagaCompraService.iniciarCompensacion(ordenId, "ERROR_EMISION")).thenReturn(false);
        when(orderPersistenceService.prepararCompensacion(ordenId, EstadoOrden.REINTEGRO_PENDIENTE))
                .thenReturn(preparada(pagoId, new BigDecimal("15000.00"), reintegroId, false, List.of()));
        doThrow(new RuntimeException("pasarela caída"))
                .when(paymentGateway).reintegrar(eq(pagoId), any(BigDecimal.class), anyString());

        service.compensar(ordenId, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_EMISION");

        verify(inventoryClient).liberarReserva(eq(reservaId), anyString(), anyString());
        verify(sagaCompraService, never()).marcarCompensada(ordenId);
        verify(sagaCompraService).registrarError(eq(ordenId), contains("reintegro=false"));
    }

    @Test
    void unReintegroYaAprobadoNoSeVuelveAPedir() {
        when(sagaCompraService.iniciarCompensacion(ordenId, "REINTENTO")).thenReturn(false);
        when(orderPersistenceService.prepararCompensacion(ordenId, null))
                .thenReturn(preparada(pagoId, new BigDecimal("15000.00"), UUID.randomUUID(),
                        true, List.of()));

        service.compensar(ordenId, null, "REINTENTO");

        verify(paymentGateway, never()).reintegrar(any(), any(), anyString());
        verify(sagaCompraService).marcarCompensada(ordenId);
    }

    @Test
    void siNoSePuedePrepararLaCompensacionLaSagaQuedaFallida() {
        when(sagaCompraService.iniciarCompensacion(ordenId, "ERROR_EMISION")).thenReturn(false);
        when(orderPersistenceService.prepararCompensacion(ordenId, EstadoOrden.REINTEGRO_PENDIENTE))
                .thenThrow(new IllegalStateException("No existe la orden " + ordenId));

        assertThatThrownBy(() -> service.compensar(ordenId,
                EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_EMISION"))
                .isInstanceOf(IllegalStateException.class);

        verify(sagaCompraService).marcarFallida(eq(ordenId), contains("No se pudo preparar"));
    }

    private OrderPersistenceService.CompensacionPreparada preparada(
            UUID pagoId, BigDecimal monto, UUID reintegroId, Boolean reintegroAprobado,
            List<OrdenItem> items) {
        return new OrderPersistenceService.CompensacionPreparada(ordenId, reservaId, eventoId,
                clienteId, pagoId, monto, reintegroId,
                Boolean.TRUE.equals(reintegroAprobado), items);
    }

    private Pago pago(EstadoPago estado) {
        Pago pago = new Pago();
        pago.setId(pagoId);
        pago.setOrdenId(ordenId);
        pago.setEstado(estado);
        pago.setMonto(new BigDecimal("15000.00"));
        return pago;
    }
}
