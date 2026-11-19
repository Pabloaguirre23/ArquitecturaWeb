package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrdersRestClient implements OrdersClient {

    private final RestClient restClient;
    private final String internalToken;

    public OrdersRestClient(RestClient.Builder builder,
                            @Value("${orders.url:http://localhost:8083}") String baseUrl,
                            @Value("${orders.timeout.connect-ms:1000}") int connectTimeoutMs,
                            @Value("${orders.timeout.read-ms:2000}") int readTimeoutMs,
                            @Value("${orders.internal-token:${INTERNAL_TOKEN:ticketya-internal-dev}}")
                            String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    @Override
    public void cancelarEvento(UUID eventoId) {
        restClient.post()
                .uri("/internal/events/{eventoId}/cancel", eventoId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .toBodilessEntity();
    }
}
