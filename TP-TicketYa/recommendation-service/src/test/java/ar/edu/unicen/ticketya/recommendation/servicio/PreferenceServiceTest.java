package ar.edu.unicen.ticketya.recommendation.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import ar.edu.unicen.ticketya.recommendation.repositorio.PreferenciaRepository;
import ar.edu.unicen.ticketya.recommendation.web.ApiException;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasRequest;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-11-14T12:00:00Z");

    @Mock
    private PreferenciaRepository preferenciaRepository;

    private PreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        preferenceService = new PreferenceService(preferenciaRepository,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void guardaPreferenciasNuevasConTiposNormalizados() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());
        when(preferenciaRepository.save(any(Preferencia.class))).thenAnswer(invocation -> {
            Preferencia preferencia = invocation.getArgument(0);
            preferencia.setId("pref-1");
            return preferencia;
        });

        PreferenciasResponse response = preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(
                        List.of("recital", "FESTIVAL", "RECITAL"),
                        List.of(" Los Piojos ", "Los Piojos"),
                        " Tandil ", null));

        assertThat(response.clienteId()).isEqualTo(CLIENTE.toString());
        assertThat(response.tipos()).containsExactly("RECITAL", "FESTIVAL");
        assertThat(response.artistas()).containsExactly("Los Piojos");
        assertThat(response.ciudad()).isEqualTo("Tandil");
        assertThat(response.actualizadoEn()).isEqualTo(AHORA);

        ArgumentCaptor<Preferencia> captor = ArgumentCaptor.forClass(Preferencia.class);
        verify(preferenciaRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("pref-1");
        assertThat(captor.getValue().getClienteId()).isEqualTo(CLIENTE.toString());
    }

    @Test
    void actualizaElMismoDocumentoSinDuplicar() {
        Preferencia existente = new Preferencia();
        existente.setId("pref-1");
        existente.setClienteId(CLIENTE.toString());
        existente.setTipos(List.of("TEATRO"));
        existente.setArtistas(List.of("Otro"));
        existente.setCiudad("Olavarria");
        when(preferenciaRepository.findByClienteId(CLIENTE.toString()))
                .thenReturn(Optional.of(existente));
        when(preferenciaRepository.save(any(Preferencia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PreferenciasResponse response = preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(List.of("DEPORTE"), List.of("Estudiantes"), "La Plata", null));

        assertThat(response.tipos()).containsExactly("DEPORTE");
        assertThat(response.artistas()).containsExactly("Estudiantes");
        assertThat(response.ciudad()).isEqualTo("La Plata");
        assertThat(existente.getId()).isEqualTo("pref-1");
        verify(preferenciaRepository).save(existente);
    }

    @Test
    void tipoDeEventoDesconocidoDevuelve400SinGuardar() {
        assertThatThrownBy(() -> preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(List.of("OPERA"), List.of(), "Tandil", null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_EVENT_TYPE");
                    assertThat(ex.getErrors()).hasSize(1);
                    assertThat(ex.getErrors().get(0).get("field")).isEqualTo("tipos[0]");
                });

        verify(preferenciaRepository, never()).save(any());
    }

    @Test
    void ciudadVaciaDevuelve400() {
        assertThatThrownBy(() -> preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(List.of(), List.of(), "   ", null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(ex.getErrors().get(0).get("field")).isEqualTo("ciudad");
                });

        verify(preferenciaRepository, never()).save(any());
    }

    @Test
    void clienteIdAjenoDevuelve403() {
        UUID otro = UUID.randomUUID();

        assertThatThrownBy(() -> preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(List.of(), List.of(), "Tandil", otro)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_PREFERENCE_OWNER");
                });

        verify(preferenciaRepository, never()).save(any());
    }

    @Test
    void clienteSinPreferenciasDevuelveVacias() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());

        PreferenciasResponse response = preferenceService.obtener(CLIENTE);

        assertThat(response.clienteId()).isEqualTo(CLIENTE.toString());
        assertThat(response.tipos()).isEmpty();
        assertThat(response.artistas()).isEmpty();
        assertThat(response.ciudad()).isNull();
        assertThat(response.actualizadoEn()).isNull();
    }

    @Test
    void consultaDevuelveLasPreferenciasGuardadas() {
        Preferencia preferencia = new Preferencia();
        preferencia.setClienteId(CLIENTE.toString());
        preferencia.setTipos(List.of("RECITAL", "FESTIVAL"));
        preferencia.setArtistas(List.of("Los Piojos"));
        preferencia.setCiudad("Tandil");
        preferencia.setActualizadoEn(AHORA);
        when(preferenciaRepository.findByClienteId(CLIENTE.toString()))
                .thenReturn(Optional.of(preferencia));

        PreferenciasResponse response = preferenceService.obtener(CLIENTE);

        assertThat(response.tipos()).containsExactly("RECITAL", "FESTIVAL");
        assertThat(response.ciudad()).isEqualTo("Tandil");
        assertThat(response.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void unaCarreraDeAltaConcurrenteReintentaSobreElDocumentoGanador() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(preferenciaExistente()));
        when(preferenciaRepository.save(any(Preferencia.class)))
                .thenThrow(new DuplicateKeyException("clienteId duplicado"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PreferenciasResponse response = preferenceService.guardar(CLIENTE,
                new PreferenciasRequest(List.of("FESTIVAL"), List.of(), "Tandil", null));

        assertThat(response.clienteId()).isEqualTo(CLIENTE.toString());
        assertThat(response.tipos()).containsExactly("FESTIVAL");
    }

    private Preferencia preferenciaExistente() {
        Preferencia preferencia = new Preferencia();
        preferencia.setId("pref-ganador");
        preferencia.setClienteId(CLIENTE.toString());
        return preferencia;
    }
}
