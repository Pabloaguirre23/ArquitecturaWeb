package ar.edu.unicen.ticketya.orders.servicio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaCompraRetryServiceTest {

    @Mock
    private SagaCompraService sagaCompraService;
    @Mock
    private OrderCompensationService orderCompensationService;

    private SagaCompraRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new SagaCompraRetryService(sagaCompraService, orderCompensationService, 2);
    }

    @Test
    void reintentaLasSagasCompensandoConservandoElMotivo() {
        SagaCompra saga = new SagaCompra(UUID.randomUUID(), PasoSaga.INVENTARIO_CONFIRMADO);
        saga.setUltimoError("pasarela caída");
        when(sagaCompraService.compensando()).thenReturn(List.of(saga));

        retryService.reintentarCompensaciones();

        verify(orderCompensationService).compensar(saga.getOrdenId(), null, "pasarela caída");
        verify(sagaCompraService, never()).marcarFallida(any(), any());
    }

    @Test
    void usaUnMotivoGenericoSiLaSagaNoTieneUltimoError() {
        SagaCompra saga = new SagaCompra(UUID.randomUUID(), PasoSaga.PAGO_APROBADO);
        when(sagaCompraService.compensando()).thenReturn(List.of(saga));

        retryService.reintentarCompensaciones();

        verify(orderCompensationService).compensar(saga.getOrdenId(), null, "REINTENTO_COMPENSACION");
    }

    @Test
    void alAgotarLosIntentosMarcaLaSagaFallida() {
        SagaCompra saga = new SagaCompra(UUID.randomUUID(), PasoSaga.PAGO_APROBADO);
        saga.setIntentos(2);
        when(sagaCompraService.compensando()).thenReturn(List.of(saga));

        retryService.reintentarCompensaciones();

        verify(sagaCompraService).marcarFallida(eq(saga.getOrdenId()), contains("agotó"));
        verify(orderCompensationService, never()).compensar(any(), any(), any());
    }

    @Test
    void unFalloEnUnaSagaNoImpideReintentarLasDemas() {
        SagaCompra primera = new SagaCompra(UUID.randomUUID(), PasoSaga.PAGO_APROBADO);
        SagaCompra segunda = new SagaCompra(UUID.randomUUID(), PasoSaga.PAGO_APROBADO);
        when(sagaCompraService.compensando()).thenReturn(List.of(primera, segunda));
        doThrow(new RuntimeException("falló"))
                .when(orderCompensationService).compensar(primera.getOrdenId(), null, "REINTENTO_COMPENSACION");

        retryService.reintentarCompensaciones();

        verify(orderCompensationService).compensar(segunda.getOrdenId(), null, "REINTENTO_COMPENSACION");
    }
}
