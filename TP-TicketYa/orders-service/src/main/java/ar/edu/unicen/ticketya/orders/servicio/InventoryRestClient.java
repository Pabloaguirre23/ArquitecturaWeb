package ar.edu.unicen.ticketya.orders.servicio;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestClient implements InventoryClient {

    public static final String CIRCUITO = "inventario";
    private static final Logger log = LoggerFactory.getLogger(InventoryRestClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public InventoryRestClient(RestClient.Builder builder,
                               @Value("${inventory.url:http://localhost:8082}") String baseUrl,
                               @Value("${inventory.internal-token:${INTERNAL_TOKEN:ticketya-internal-dev}}")
                               String internalToken,
                               @Value("${inventory.timeout.connect-ms:2000}") long connectMs,
                               @Value("${inventory.timeout.read-ms:5000}") long readMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readMs));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalToken = internalToken;
    }

    @Override
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "obtenerReservaNoDisponible")
    @Retry(name = CIRCUITO)
    public ReservationDto obtenerReserva(UUID reservaId) {
        return restClient.get()
                .uri("/internal/reservations/{reservaId}", reservaId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(ReservationDto.class);
    }

    @Override
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "confirmacionNoDisponible")
    @Retry(name = CIRCUITO)
    public void confirmarReserva(UUID reservaId, String idempotencyKey) {
        restClient.post()
                .uri("/internal/reservations/{reservaId}/confirm", reservaId)
                .header("X-Internal-Token", internalToken)
                .header("Idempotency-Key", idempotencyKey)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "liberacionNoDisponible")
    @Retry(name = CIRCUITO)
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
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "devolucionNoDisponible")
    @Retry(name = CIRCUITO)
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

    @SuppressWarnings("unused")
    private ReservationDto obtenerReservaNoDisponible(UUID reservaId, Throwable causa) {
        if (causa instanceof org.springframework.web.client.HttpClientErrorException clientError) {
            // Un 404/403 de inventory es una respuesta de negocio, no una caída:
            // OrderService la traduce a RESERVATION_NOT_FOUND o al error original.
            throw clientError;
        }
        throw noDisponible("consultar la reserva " + reservaId, causa);
    }

    @SuppressWarnings("unused")
    private void confirmacionNoDisponible(UUID reservaId, String idempotencyKey, Throwable causa) {
        throw noDisponible("confirmar la reserva " + reservaId, causa);
    }

    @SuppressWarnings("unused")
    private void liberacionNoDisponible(UUID reservaId, String motivo, String idempotencyKey, Throwable causa) {
        throw noDisponible("liberar la reserva " + reservaId, causa);
    }

    @SuppressWarnings("unused")
    private void devolucionNoDisponible(ReturnDto devolucion, Throwable causa) {
        throw noDisponible("devolver los ítems de la referencia " + devolucion.referencia(), causa);
    }

    private static InventoryUnavailableException noDisponible(String operacion, Throwable causa) {
        log.warn("Fallback de inventory: no se pudo {}: {}", operacion, causa.toString());
        return new InventoryUnavailableException("inventory no está disponible para " + operacion, causa);
    }

    private record ReleaseDto(String motivo) {
    }
}
