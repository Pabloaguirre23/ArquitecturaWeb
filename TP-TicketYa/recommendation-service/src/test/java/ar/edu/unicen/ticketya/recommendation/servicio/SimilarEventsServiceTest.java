package ar.edu.unicen.ticketya.recommendation.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.repositorio.EventoVistoRepository;
import ar.edu.unicen.ticketya.recommendation.repositorio.HistorialCompraRepository;
import ar.edu.unicen.ticketya.recommendation.web.dto.EventoSimilarItem;
import ar.edu.unicen.ticketya.recommendation.web.dto.EventosSimilaresResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimilarEventsServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OBJETIVO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final String OTRO1 = "44444444-4444-4444-8444-444444444444";
    private static final String OTRO2 = "55555555-5555-4555-8555-555555555555";
    private static final String OTRO3 = "66666666-6666-4666-8666-666666666666";
    private static final String E1 = OBJETIVO.toString();
    private static final String E2 = "d0000000-0000-4000-8000-000000000002";
    private static final String E3 = "d0000000-0000-4000-8000-000000000003";
    private static final String E4 = "d0000000-0000-4000-8000-000000000004";
    private static final String E5 = "d0000000-0000-4000-8000-000000000005";

    @Mock
    private HistorialCompraRepository historialRepository;
    @Mock
    private EventoVistoRepository eventoVistoRepository;

    private SimilarEventsService similarEventsService;

    @BeforeEach
    void setUp() {
        similarEventsService = new SimilarEventsService(historialRepository, eventoVistoRepository,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void ordenaPorCompradoresEnComunYExcluyeElEventoYLoYaComprado() {
        when(historialRepository.findByClienteId(CLIENTE.toString()))
                .thenReturn(List.of(compra(CLIENTE.toString(), E3)));
        when(historialRepository.findByEventoIdIn(List.of(E1)))
                .thenReturn(List.of(compra(OTRO1, E1), compra(OTRO2, E1), compra(OTRO3, E1),
                        compra(CLIENTE.toString(), E1)));
        when(historialRepository.findByClienteIdIn(anyCollection())).thenReturn(List.of(
                compra(OTRO1, E2), compra(OTRO1, E5), compra(OTRO1, E1),
                compra(OTRO2, E2), compra(OTRO2, E5), compra(OTRO2, E4),
                compra(OTRO3, E2), compra(OTRO3, E4), compra(OTRO3, E4), compra(OTRO3, E3)));
        when(eventoVistoRepository.findAllById(anyIterable())).thenReturn(List.of(
                eventoVisto(E2, "Festival de Verano", "2027-03-15T00:00:00Z", "PUBLICADO"),
                eventoVisto(E4, "Charla abierta", "2027-05-01T00:00:00Z", "PUBLICADO"),
                eventoVisto(E5, "El Avaro", "2027-04-01T00:00:00Z", "PUBLICADO")));

        EventosSimilaresResponse response = similarEventsService.similares(CLIENTE, OBJETIVO);

        assertThat(response.eventoId()).isEqualTo(E1);
        assertThat(response.generadoEn()).isEqualTo(AHORA);
        assertThat(response.similares()).extracting(EventoSimilarItem::eventoId)
                .containsExactly(E2, E5, E4);
        assertThat(response.similares()).extracting(EventoSimilarItem::compradoresEnComun)
                .containsExactly(3L, 2L, 2L);
    }

    @Test
    void sinOtrosCompradoresDevuelveListaVacia() {
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(historialRepository.findByEventoIdIn(List.of(E1))).thenReturn(List.of());

        EventosSimilaresResponse response = similarEventsService.similares(CLIENTE, OBJETIVO);

        assertThat(response.eventoId()).isEqualTo(E1);
        assertThat(response.similares()).isEmpty();
    }

    @Test
    void excluyeCanceladosPasadosYSinSnapshot() {
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(historialRepository.findByEventoIdIn(List.of(E1))).thenReturn(List.of(compra(OTRO1, E1)));
        when(historialRepository.findByClienteIdIn(anyCollection())).thenReturn(List.of(
                compra(OTRO1, E2), compra(OTRO1, E3), compra(OTRO1, E4), compra(OTRO1, E5)));
        when(eventoVistoRepository.findAllById(anyIterable())).thenReturn(List.of(
                eventoVisto(E2, "Vigente", "2027-03-15T00:00:00Z", "PUBLICADO"),
                eventoVisto(E3, "Cancelado", "2027-04-01T00:00:00Z", "CANCELADO"),
                eventoVisto(E4, "Pasado", "2026-01-01T00:00:00Z", "PUBLICADO")));

        EventosSimilaresResponse response = similarEventsService.similares(CLIENTE, OBJETIVO);

        assertThat(response.similares()).extracting(EventoSimilarItem::eventoId)
                .containsExactly(E2);
    }

    private HistorialCompra compra(String clienteId, String eventoId) {
        HistorialCompra compra = new HistorialCompra();
        compra.setClienteId(clienteId);
        compra.setEventoId(eventoId);
        compra.setTipo("FESTIVAL");
        compra.setArtistas(List.of("Los Piojos"));
        compra.setCiudad("Tandil");
        compra.setFechaEvento(Instant.parse("2027-03-15T00:00:00Z"));
        return compra;
    }

    private EventoVisto eventoVisto(String eventoId, String nombre, String fecha, String estado) {
        EventoVisto evento = new EventoVisto();
        evento.setEventoId(eventoId);
        evento.setNombre(nombre);
        evento.setTipo("FESTIVAL");
        evento.setArtistas(List.of("Los Piojos"));
        evento.setCiudad("Tandil");
        evento.setFechaEvento(Instant.parse(fecha));
        evento.setEstado(estado);
        return evento;
    }
}
