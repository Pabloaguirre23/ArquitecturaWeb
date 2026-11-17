package ar.edu.unicen.ticketya.orders.servicio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente del endpoint público de detalle de catalog. Lee solo los campos del
 * snapshot del evento; el resto de la respuesta se ignora. Los timeouts son
 * cortos a propósito: este enriquecimiento es best-effort y nunca debe trabar
 * la compra.
 */
@Component
public class CatalogRestClient implements CatalogClient {

    private static final String RUTA_DETALLE = "/api/catalog/events/{eventoId}";

    private final RestClient restClient;

    public CatalogRestClient(RestClient.Builder builder,
                             @Value("${catalog.url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public EventoSnapshot obtenerEvento(UUID eventoId) {
        EventoDetalle detalle = restClient.get()
                .uri(RUTA_DETALLE, eventoId)
                .retrieve()
                .body(EventoDetalle.class);
        if (detalle == null) {
            return null;
        }
        Map<UUID, String> sectores = new LinkedHashMap<>();
        if (detalle.sectores() != null) {
            detalle.sectores().stream()
                    .filter(s -> s.sectorId() != null)
                    .forEach(s -> sectores.put(s.sectorId(), s.nombre()));
        }
        return new EventoSnapshot(
                detalle.tipo(),
                detalle.participantes() == null ? List.of() : List.copyOf(detalle.participantes()),
                detalle.ciudad(),
                detalle.organizadorId(),
                sectores);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EventoDetalle(
            String tipo,
            String ciudad,
            UUID organizadorId,
            List<String> participantes,
            List<SectorDetalle> sectores) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorDetalle(UUID sectorId, String nombre) {
    }
}
