package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
class OrderPersistenceServiceTest {

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private OrdenItemRepository ordenItemRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReintegroRepository reintegroRepository;
    @Mock
    private SagaCompraService sagaCompraService;
    @Mock
    private OutboxService outboxService;

    private OrderPersistenceService service;
    private final UUID ordenId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderPersistenceService(ordenRepository, ordenItemRepository, pagoRepository,
                reintegroRepository, sagaCompraService, outboxService);
    }

    @Test
    void crearPendienteGuardaElSnapshotQueUsaraElEventoOrdenPagada() {
        UUID clienteId = UUID.randomUUID();
        String email = "cliente@example.com";
        UUID organizadorId = UUID.randomUUID();
        ReservationDto reserva = new ReservationDto(UUID.randomUUID(), UUID.randomUUID(), "Festival",
                OffsetDateTime.parse("2027-03-15T00:00:00Z"), clienteId, "VIGENTE",
                OffsetDateTime.parse("2026-11-15T12:10:00Z"),
                List.of(new ReservationDto.Item(UUID.randomUUID(), null, 2,
                        new BigDecimal("12500.00"), new BigDecimal("25000.00"))),
                new BigDecimal("25000.00"), email, organizadorId, "Campo", "FESTIVAL",
                List.of("Los Piojos", "Callejeros"), "Tandil");
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> {
            Orden orden = inv.getArgument(0);
            orden.setId(ordenId);
            return orden;
        });
        when(ordenItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        service.crearPendiente(reserva, clienteId, null,
                new CrearOrdenRequest(reserva.id(), ar.edu.unicen.ticketya.orders.dominio.MedioPago.TARJETA,
                        "APROBADO"));

        ArgumentCaptor<Orden> captor = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepository).save(captor.capture());
        Orden orden = captor.getValue();
        assertThat(orden.getClienteEmail()).isEqualTo(email);
        assertThat(orden.getEventoOrganizadorId()).isEqualTo(organizadorId);
        assertThat(orden.getSectorNombre()).isEqualTo("Campo");
        assertThat(orden.getEventoTipo()).isEqualTo("FESTIVAL");
        assertThat(orden.getEventoArtistas()).isEqualTo("[\"Los Piojos\",\"Callejeros\"]");
        assertThat(orden.getEventoCiudad()).isEqualTo("Tandil");
    }

    @Test
    void prepararCompensacionRegistraOrdenReintegradaConElMismoMotivo() {
        Orden orden = orden();
        Pago pago = new Pago();
        pago.setId(UUID.randomUUID());
        pago.setOrdenId(ordenId);
        pago.setMonto(new BigDecimal("25000.00"));
        pago.setEstado(EstadoPago.APROBADO);
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of(pago));
        when(reintegroRepository.findByPagoId(pago.getId())).thenReturn(List.of());
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ordenItemRepository.findByOrdenId(ordenId)).thenReturn(List.of());

        service.prepararCompensacion(ordenId, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_EMISION");

        verify(outboxService).registrar(org.mockito.ArgumentMatchers.eq("orden.reintegrada"),
                org.mockito.ArgumentMatchers.eq(ordenId), any());
    }

    @Test
    void sinPagoAprobadoNoSePublicaOrdenReintegrada() {
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden()));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of());

        service.prepararCompensacion(ordenId, EstadoOrden.RECHAZADA, "PAGO_RECHAZADO");

        verify(outboxService, never()).registrar(any(), any(), any());
    }

    private Orden orden() {
        Orden orden = new Orden();
        orden.setId(ordenId);
        orden.setClienteId(UUID.randomUUID());
        orden.setEventoId(UUID.randomUUID());
        orden.setEventoNombre("Festival");
        orden.setEventoFecha(OffsetDateTime.parse("2027-03-15T00:00:00Z"));
        orden.setReservaId(UUID.randomUUID());
        orden.setEstado(EstadoOrden.PENDIENTE_PAGO);
        orden.setTotal(new BigDecimal("25000.00"));
        return orden;
    }
}
