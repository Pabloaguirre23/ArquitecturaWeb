package ar.edu.unicen.ticketya.inventory.servicio;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CatalogClient {

    public static final String CIRCUITO = "catalog";
    private static final String SIN_CORRELACION = "sin-correlacion";
    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${catalog.url:http://localhost:8081}") String baseUrl,
                         @Value("${catalog.timeout.connect-ms:2000}") long connectMs,
                         @Value("${catalog.timeout.read-ms:2000}") long readMs,
                         @Value("${catalog.internal-token:${INTERNAL_TOKEN:ticketya-internal-dev}}")
                         String internalToken) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.internalToken = internalToken;
    }

    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "disponibilidadNoAvisada")
    @Retry(name = CIRCUITO)
    public void notificarDisponibilidad(UUID eventoId, boolean agotado) {
        String correlacion = correlacion();
        var request = restClient.patch()
                .uri("/internal/events/{eventoId}/availability", eventoId)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON);
        if (!SIN_CORRELACION.equals(correlacion)) {
            request = request.header("X-Correlation-Id", correlacion);
        }
        request.body(Map.of("agotado", agotado)).retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void disponibilidadNoAvisada(UUID eventoId, boolean agotado, Throwable causa) {
        log.warn("Fallback de catalog: no se avisó la disponibilidad. evento={} agotado={} correlacion={}",
                eventoId, agotado, correlacion(), causa);
    }

    private static String correlacion() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            String header = servlet.getRequest().getHeader("X-Correlation-Id");
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return SIN_CORRELACION;
    }
}
