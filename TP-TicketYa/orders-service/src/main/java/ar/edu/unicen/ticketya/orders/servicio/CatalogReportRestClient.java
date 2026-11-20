package ar.edu.unicen.ticketya.orders.servicio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente del endpoint interno de reporte de catalog. Usa el token interno y
 * timeouts cortos: si catalog no responde, ReportService degradea con cache o
 * con el snapshot de orders (tarea 6.5).
 */
@Component
public class CatalogReportRestClient implements CatalogReportClient {

    private static final String RUTA_REPORTE = "/internal/events/{eventoId}/report";

    private final RestClient restClient;
    private final String internalToken;

    public CatalogReportRestClient(RestClient.Builder builder,
                                   @Value("${catalog.url:http://localhost:8081}") String baseUrl,
                                   @Value("${catalog.internal-token:${INTERNAL_TOKEN:ticketya-internal-dev}}")
                                   String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalToken = internalToken;
    }

    @Override
    public CatalogReport obtenerReporte(UUID eventoId) {
        ReporteCatalogo response = restClient.get()
                .uri(RUTA_REPORTE, eventoId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        (request, errorResponse) -> {
                            throw new CatalogEventNotFoundException(eventoId);
                        })
                .body(ReporteCatalogo.class);
        if (response == null) {
            return null;
        }
        List<CatalogSector> sectores = response.sectores() == null
                ? List.of()
                : response.sectores().stream()
                        .filter(s -> s != null && s.sectorId() != null)
                        .map(s -> new CatalogSector(s.sectorId(), s.nombre(), s.tipo(),
                                s.capacidad(), s.precio()))
                        .toList();
        return new CatalogReport(response.eventoId(), response.nombre(),
                response.organizadorId(), sectores);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReporteCatalogo(
            UUID eventoId,
            String nombre,
            UUID organizadorId,
            List<SectorCatalogo> sectores) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorCatalogo(
            UUID sectorId,
            String nombre,
            String tipo,
            Integer capacidad,
            BigDecimal precio) {
    }
}
