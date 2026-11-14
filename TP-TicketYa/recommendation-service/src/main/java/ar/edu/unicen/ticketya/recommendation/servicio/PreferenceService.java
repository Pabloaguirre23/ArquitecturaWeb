package ar.edu.unicen.ticketya.recommendation.servicio;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import ar.edu.unicen.ticketya.recommendation.dominio.TipoEvento;
import ar.edu.unicen.ticketya.recommendation.repositorio.PreferenciaRepository;
import ar.edu.unicen.ticketya.recommendation.web.ApiException;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasRequest;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    private final PreferenciaRepository preferenciaRepository;
    private final Clock clock;

    public PreferenceService(PreferenciaRepository preferenciaRepository, Clock clock) {
        this.preferenciaRepository = preferenciaRepository;
        this.clock = clock;
    }

    public PreferenciasResponse obtener(UUID clienteId) {
        return preferenciaRepository.findByClienteId(clienteId.toString())
                .map(this::toResponse)
                .orElseGet(() -> vacias(clienteId));
    }

    public PreferenciasResponse guardar(UUID clienteId, PreferenciasRequest request) {
        if (request.clienteId() != null && !request.clienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_PREFERENCE_OWNER",
                    "Las preferencias pertenecen a otro cliente");
        }

        List<String> tipos = normalizarTipos(request.tipos());
        List<String> artistas = normalizarArtistas(request.artistas());
        String ciudad = request.ciudad() == null ? "" : request.ciudad().trim();
        if (ciudad.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "La ciudad es obligatoria",
                    List.of(Map.of("field", "ciudad", "message", "la ciudad no puede estar vacía")));
        }

        Instant ahora = Instant.now(clock);
        Preferencia preferencia = preferenciaRepository.findByClienteId(clienteId.toString())
                .orElseGet(() -> {
                    Preferencia nueva = new Preferencia();
                    nueva.setClienteId(clienteId.toString());
                    return nueva;
                });
        preferencia.setTipos(tipos);
        preferencia.setArtistas(artistas);
        preferencia.setCiudad(ciudad);
        preferencia.setActualizadoEn(ahora);

        return toResponse(guardarSinDuplicar(preferencia));
    }

    private Preferencia guardarSinDuplicar(Preferencia preferencia) {
        try {
            return preferenciaRepository.save(preferencia);
        } catch (DuplicateKeyException ex) {
            // Dos primeras escrituras concurrentes del mismo cliente: el índice
            // único decide y el perdedor actualiza el documento ganador.
            return preferenciaRepository.findByClienteId(preferencia.getClienteId())
                    .map(actual -> {
                        actual.setTipos(preferencia.getTipos());
                        actual.setArtistas(preferencia.getArtistas());
                        actual.setCiudad(preferencia.getCiudad());
                        actual.setActualizadoEn(preferencia.getActualizadoEn());
                        return preferenciaRepository.save(actual);
                    })
                    .orElseThrow(() -> ex);
        }
    }

    private List<String> normalizarTipos(List<String> tipos) {
        Set<String> normalizados = new LinkedHashSet<>();
        if (tipos == null) {
            return new ArrayList<>();
        }
        for (int i = 0; i < tipos.size(); i++) {
            String tipo = tipos.get(i) == null ? "" : tipos.get(i).trim().toUpperCase(Locale.ROOT);
            if (tipo.isEmpty()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "El tipo de evento no puede estar vacío",
                        List.of(Map.of("field", "tipos[" + i + "]", "message", "no puede estar vacío")));
            }
            try {
                normalizados.add(TipoEvento.valueOf(tipo).name());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_EVENT_TYPE",
                        "El tipo de evento " + tipo + " no es conocido",
                        List.of(Map.of("field", "tipos[" + i + "]",
                                "message", "valores admitidos: " + valoresDeTipo())));
            }
        }
        return new ArrayList<>(normalizados);
    }

    private List<String> normalizarArtistas(List<String> artistas) {
        if (artistas == null) {
            return new ArrayList<>();
        }
        Set<String> vistos = new LinkedHashSet<>();
        List<String> normalizados = new ArrayList<>();
        for (int i = 0; i < artistas.size(); i++) {
            String artista = artistas.get(i) == null ? "" : artistas.get(i).trim();
            if (artista.isEmpty()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "El artista no puede estar vacío",
                        List.of(Map.of("field", "artistas[" + i + "]", "message", "no puede estar vacío")));
            }
            if (vistos.add(artista.toLowerCase(Locale.ROOT))) {
                normalizados.add(artista);
            }
        }
        return normalizados;
    }

    private List<String> valoresDeTipo() {
        List<String> valores = new ArrayList<>();
        for (TipoEvento tipo : TipoEvento.values()) {
            valores.add(tipo.name());
        }
        return valores;
    }

    private PreferenciasResponse toResponse(Preferencia preferencia) {
        return new PreferenciasResponse(
                preferencia.getClienteId(),
                List.copyOf(preferencia.getTipos()),
                List.copyOf(preferencia.getArtistas()),
                preferencia.getCiudad(),
                preferencia.getActualizadoEn());
    }

    private PreferenciasResponse vacias(UUID clienteId) {
        return new PreferenciasResponse(clienteId.toString(), List.of(), List.of(), null, null);
    }
}
