package ar.edu.unicen.ticketya.orders.servicio;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestClient implements InventoryClient {

    private final RestClient restClient;
    private final String internalToken;

    public InventoryRestClient(RestClient.Builder builder,
                               @Value("${inventory.url:http://localhost:8082}") String baseUrl,
                               @Value("${inventory.internal-token:${INTERNAL_TOKEN:ticketya-internal-dev}}")
                               String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalToken = internalToken;
    }

    @Override
    public ReservationDto obtenerReserva(UUID reservaId) {
        return restClient.get()
                .uri("/internal/reservations/{reservaId}", reservaId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(ReservationDto.class);
    }

    @Override
    public void confirmarReserva(UUID reservaId, String idempotencyKey) {
        restClient.post()
                .uri("/internal/reservations/{reservaId}/confirm", reservaId)
                .header("X-Internal-Token", internalToken)
                .header("Idempotency-Key", idempotencyKey)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void liberarReserva(UUID reservaId, String motivo, String idempotencyKey) {
        restClient.post()
                .uri("/internal/reservations/{reservaId}/release", reservaId)
                .header("X-Internal-Token", internalToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseDto(motivo))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void devolverItems(ReturnDto devolucion) {
        restClient.post()
                .uri("/internal/returns")
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", devolucion.referencia().toString())
                .body(devolucion)
                .retrieve()
                .toBodilessEntity();
    }

    private record ReleaseDto(String motivo) {
    }
}
