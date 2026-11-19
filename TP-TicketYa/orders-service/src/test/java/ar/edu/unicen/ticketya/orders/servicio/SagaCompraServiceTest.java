package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoSaga;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.SagaCompraRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaCompraServiceTest {

    @Mock
    private SagaCompraRepository sagaCompraRepository;

    private SagaCompraService service;

    @BeforeEach
    void setUp() {
        service = new SagaCompraService(sagaCompraRepository);
    }

    @Test
    void laSagaNaceEnOrdenCreadaYEnProceso() {
        UUID ordenId = UUID.randomUUID();
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SagaCompra saga = service.iniciar(ordenId);

        assertThat(saga.getOrdenId()).isEqualTo(ordenId);
        assertThat(saga.getPaso()).isEqualTo(PasoSaga.ORDEN_CREADA);
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.EN_PROCESO);
        verify(sagaCompraRepository).save(saga);
    }

    @Test
    void unPasoNoRetrocedeYUnReintentoNoBajaElUltimoPaso() {
        UUID ordenId = UUID.randomUUID();
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.ORDEN_CREADA);
        when(sagaCompraRepository.findById(ordenId)).thenReturn(Optional.of(saga));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.pasoCompletado(ordenId, PasoSaga.PAGO_APROBADO);
        service.pasoCompletado(ordenId, PasoSaga.ORDEN_CREADA);
        service.pasoCompletado(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);

        assertThat(saga.getPaso()).isEqualTo(PasoSaga.INVENTARIO_CONFIRMADO);
    }

    @Test
    void completarMarcaElPasoFinalYElEstadoCompletada() {
        UUID ordenId = UUID.randomUUID();
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
        when(sagaCompraRepository.findById(ordenId)).thenReturn(Optional.of(saga));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.completarEnTransaccion(ordenId);

        assertThat(saga.getPaso()).isEqualTo(PasoSaga.ENTRADAS_EMITIDAS);
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.COMPLETADA);
    }

    @Test
    void registrarErrorIncrementaIntentosYGuardaElMotivo() {
        UUID ordenId = UUID.randomUUID();
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.PAGO_APROBADO);
        when(sagaCompraRepository.findById(ordenId)).thenReturn(Optional.of(saga));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarError(ordenId, "timeout");

        assertThat(saga.getIntentos()).isEqualTo(1);
        assertThat(saga.getUltimoError()).isEqualTo("timeout");
    }

    @Test
    void antesDeConfirmarInventarioLaCompensacionLiberaLaReserva() {
        UUID ordenId = UUID.randomUUID();
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.PAGO_APROBADO);
        when(sagaCompraRepository.findById(ordenId)).thenReturn(Optional.of(saga));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean ventaConfirmada = service.iniciarCompensacion(ordenId, "PAGO_RECHAZADO");

        assertThat(ventaConfirmada).isFalse();
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.COMPENSANDO);
        assertThat(saga.getUltimoError()).isEqualTo("PAGO_RECHAZADO");
    }

    @Test
    void conInventarioConfirmadoLaCompensacionDevuelveItems() {
        UUID ordenId = UUID.randomUUID();
        SagaCompra saga = new SagaCompra(ordenId, PasoSaga.INVENTARIO_CONFIRMADO);
        when(sagaCompraRepository.findById(ordenId)).thenReturn(Optional.of(saga));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean ventaConfirmada = service.iniciarCompensacion(ordenId, "ERROR_EMISION");

        assertThat(ventaConfirmada).isTrue();
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.COMPENSANDO);
    }

    @Test
    void marcarCompensadaYFallidaDejanElEstadoFinal() {
        UUID compensada = UUID.randomUUID();
        UUID fallida = UUID.randomUUID();
        SagaCompra sagaCompensada = new SagaCompra(compensada, PasoSaga.PAGO_APROBADO);
        SagaCompra sagaFallida = new SagaCompra(fallida, PasoSaga.PAGO_APROBADO);
        when(sagaCompraRepository.findById(compensada)).thenReturn(Optional.of(sagaCompensada));
        when(sagaCompraRepository.findById(fallida)).thenReturn(Optional.of(sagaFallida));
        when(sagaCompraRepository.save(any(SagaCompra.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.marcarCompensada(compensada);
        service.marcarFallida(fallida, "agotó reintentos");

        assertThat(sagaCompensada.getEstado()).isEqualTo(EstadoSaga.COMPENSADA);
        assertThat(sagaFallida.getEstado()).isEqualTo(EstadoSaga.FALLIDA);
        assertThat(sagaFallida.getUltimoError()).isEqualTo("agotó reintentos");
    }

    @Test
    void compensandoDevuelveLasSagasPendientesDeReintento() {
        SagaCompra saga = new SagaCompra(UUID.randomUUID(), PasoSaga.PAGO_APROBADO);
        when(sagaCompraRepository.findByEstado(EstadoSaga.COMPENSANDO)).thenReturn(List.of(saga));

        assertThat(service.compensando()).containsExactly(saga);
    }
}
