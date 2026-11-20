package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.CompradorEventoService;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

@ExtendWith(MockitoExtension.class)
class OrdenPagadaListenerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Mock
    private NotificationService notificationService;

    @Mock
    private CompradorEventoService compradorEventoService;

    private OrdenPagadaListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrdenPagadaListener(notificationService, compradorEventoService,
                new DestinatarioEmail("ticketya.local"), MAPPER);
    }

    @Test
    void generaLaNotificacionDeCompraYUsaEmailDeterministicoSiFalta() {
        UUID eventId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(eventId.toString(), OrdenEvent.ORDEN_PAGADA,
                payloadPagada(clienteId, eventoId, OrdenPagadaPayload.ESTADO_PAGADA, null)));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        verify(compradorEventoService).registrar(any(OrdenPagadaPayload.class));
        Notificacion notificacion = captor.getValue();
        assertThat(notificacion.getEventId()).isEqualTo(eventId);
        assertThat(notificacion.getTipo()).isEqualTo(OrdenEvent.ORDEN_PAGADA);
        assertThat(notificacion.getDestinatarioId()).isEqualTo(clienteId);
        assertThat(notificacion.getDestinatarioEmail())
                .isEqualTo("cliente-" + clienteId + "@ticketya.local");
        assertThat(notificacion.getAsunto()).contains("Festival de Rock");
        assertThat(notificacion.getContenido()).contains("fue confirmada");
        assertThat(notificacion.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void respetaElEmailDelPayloadCuandoViene() {
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(UUID.randomUUID().toString(), OrdenEvent.ORDEN_PAGADA,
                payloadPagada(UUID.randomUUID(), UUID.randomUUID(),
                        OrdenPagadaPayload.ESTADO_PAGADA, "cliente@example.com")));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        assertThat(captor.getValue().getDestinatarioEmail()).isEqualTo("cliente@example.com");
    }

    @Test
    void laReintegradaAvisaElReintegroSinRegistrarComprador() {
        UUID eventId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(eventId.toString(), OrdenEvent.ORDEN_REINTEGRADA,
                payloadReintegrada(clienteId, new BigDecimal("25000.00"), "PAGO_RECHAZADO")));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        Notificacion notificacion = captor.getValue();
        assertThat(notificacion.getEventId()).isEqualTo(eventId);
        assertThat(notificacion.getTipo()).isEqualTo(OrdenEvent.ORDEN_REINTEGRADA);
        assertThat(notificacion.getDestinatarioId()).isEqualTo(clienteId);
        assertThat(notificacion.getAsunto()).contains("Reintegro de la orden");
        assertThat(notificacion.getContenido())
                .contains("reintegrada")
                .contains("25000.00")
                .contains("PAGO_RECHAZADO");
        assertThat(notificacion.getCorrelationId()).isEqualTo("corr-1");
        verifyNoInteractions(compradorEventoService);
    }

    @Test
    void laReintegradaSinClienteVaALaDlqSinNotificar() {
        OrdenEvent evento = evento(UUID.randomUUID().toString(), OrdenEvent.ORDEN_REINTEGRADA,
                payloadReintegrada(null, null, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    @Test
    void laDevolucionAprobadaConfirmaElReintegroAlCliente() {
        UUID eventId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(eventId.toString(), OrdenEvent.DEVOLUCION_APROBADA,
                payloadDevolucion(clienteId, new BigDecimal("25000.00"))));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        Notificacion notificacion = captor.getValue();
        assertThat(notificacion.getEventId()).isEqualTo(eventId);
        assertThat(notificacion.getTipo()).isEqualTo(OrdenEvent.DEVOLUCION_APROBADA);
        assertThat(notificacion.getDestinatarioId()).isEqualTo(clienteId);
        assertThat(notificacion.getAsunto()).contains("Devolución aprobada");
        assertThat(notificacion.getContenido())
                .contains("Se aprobó la devolución")
                .contains("25000.00");
        assertThat(notificacion.getCorrelationId()).isEqualTo("corr-1");
        verifyNoInteractions(compradorEventoService);
    }

    @Test
    void laDevolucionAprobadaSinClienteVaALaDlqSinNotificar() {
        OrdenEvent evento = evento(UUID.randomUUID().toString(), OrdenEvent.DEVOLUCION_APROBADA,
                payloadDevolucion(null, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    @Test
    void unEstadoDistintoDePagadaVaALaDlqSinNotificar() {
        OrdenEvent evento = evento(UUID.randomUUID().toString(), OrdenEvent.ORDEN_PAGADA,
                payloadPagada(UUID.randomUUID(), UUID.randomUUID(), "PENDIENTE", null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    @Test
    void unTipoInesperadoVaALaDlqSinNotificar() {
        OrdenEvent evento = evento(UUID.randomUUID().toString(), "entrada.utilizada",
                payloadPagada(UUID.randomUUID(), UUID.randomUUID(),
                        OrdenPagadaPayload.ESTADO_PAGADA, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    @Test
    void unPayloadSinClienteVaALaDlqSinNotificar() {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("ordenId", UUID.randomUUID().toString());
        payload.put("eventoId", UUID.randomUUID().toString());
        payload.put("eventoNombre", "Festival");
        payload.put("eventoTipo", "FESTIVAL");
        payload.put("ciudad", "Tandil");
        payload.put("estado", OrdenPagadaPayload.ESTADO_PAGADA);
        OrdenEvent evento = evento(UUID.randomUUID().toString(), OrdenEvent.ORDEN_PAGADA, payload);

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    @Test
    void unEventIdQueNoEsUuidVaALaDlqSinNotificar() {
        OrdenEvent evento = evento("no-es-uuid", OrdenEvent.ORDEN_PAGADA,
                payloadPagada(UUID.randomUUID(), UUID.randomUUID(),
                        OrdenPagadaPayload.ESTADO_PAGADA, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService, compradorEventoService);
    }

    private OrdenEvent evento(String eventId, String tipo, ObjectNode payload) {
        return new OrdenEvent(eventId, tipo, Instant.parse("2026-11-15T12:00:00Z"),
                "corr-1", 1, payload);
    }

    private ObjectNode payloadPagada(UUID clienteId, UUID eventoId, String estado, String email) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("ordenId", UUID.randomUUID().toString());
        payload.put("clienteId", clienteId == null ? null : clienteId.toString());
        payload.put("eventoId", eventoId.toString());
        payload.put("eventoNombre", "Festival de Rock Tandil 2027");
        payload.put("eventoTipo", "FESTIVAL");
        payload.putArray("artistas").add("Los Piojos");
        payload.put("ciudad", "Tandil");
        payload.put("fechaEvento", "2027-03-15T00:00:00Z");
        payload.put("estado", estado);
        if (email != null) {
            payload.put("clienteEmail", email);
        }
        return payload;
    }

    private ObjectNode payloadReintegrada(UUID clienteId, BigDecimal monto, String motivo) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("ordenId", UUID.randomUUID().toString());
        payload.put("clienteId", clienteId == null ? null : clienteId.toString());
        payload.put("eventoId", UUID.randomUUID().toString());
        if (monto != null) {
            payload.put("monto", monto);
        }
        if (motivo != null) {
            payload.put("motivo", motivo);
        }
        return payload;
    }

    private ObjectNode payloadDevolucion(UUID clienteId, BigDecimal monto) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("devolucionId", UUID.randomUUID().toString());
        payload.put("ordenId", UUID.randomUUID().toString());
        payload.put("entradaId", UUID.randomUUID().toString());
        payload.put("clienteId", clienteId == null ? null : clienteId.toString());
        if (monto != null) {
            payload.put("monto", monto);
        }
        return payload;
    }
}
