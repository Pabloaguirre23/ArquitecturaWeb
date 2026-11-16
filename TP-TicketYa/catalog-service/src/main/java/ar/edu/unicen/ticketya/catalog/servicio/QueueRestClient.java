package ar.edu.unicen.ticketya.catalog.servicio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class QueueRestClient implements QueueClient {

    static final int MAX_INTENTOS = 3;
    static final Duration ESPERA_BASE = Duration.ofMillis(100);

    private final RestClient restClient;
    private final String internalToken;

    public QueueRestClient(RestClient.Builder builder,
                           @Value("${queue.url:http://localhost:8086}") String baseUrl,
                           @Value("${queue.internal-token:ticketya-internal-dev}") String internalToken,
                           @Value("${queue.timeout.connect-ms:2000}") int connectTimeoutMs,
                           @Value("${queue.timeout.read-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    @Override
    public void abrir(UUID eventoId) {
        conReintentos(() -> {
            restClient.post()
                    .uri("/internal/events/{eventoId}", eventoId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void cerrar(UUID eventoId, String motivo) {
        conReintentos(() -> {
            restClient.post()
                    .uri("/internal/events/{eventoId}/close", eventoId)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("motivo", motivo))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T conReintentos(Supplier<T> operacion) {
        RuntimeException ultimo = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (HttpClientErrorException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                ultimo = ex;
                if (intento < MAX_INTENTOS) {
                    esperar(intento);
                }
            }
        }
        throw ultimo;
    }

    private void esperar(int intento) {
        try {
            Thread.sleep(ESPERA_BASE.toMillis() * intento);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el reintento contra queue", ex);
        }
    }
}
