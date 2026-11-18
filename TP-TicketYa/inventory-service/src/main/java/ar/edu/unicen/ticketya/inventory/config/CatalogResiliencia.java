package ar.edu.unicen.ticketya.inventory.config;

import ar.edu.unicen.ticketya.inventory.servicio.CatalogClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CatalogResiliencia {

    private static final Logger log = LoggerFactory.getLogger(CatalogResiliencia.class);

    public CatalogResiliencia(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
        circuitBreakers.circuitBreaker(CatalogClient.CIRCUITO).getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuito de catalog cambio de estado: {}", event.getStateTransition()));
        retries.retry(CatalogClient.CIRCUITO).getEventPublisher()
                .onRetry(event -> log.warn(
                        "Reintento {} hacia catalog: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().toString()))
                .onError(event -> log.warn(
                        "Se agotaron los reintentos hacia catalog: {}",
                        event.getLastThrowable().toString()));
    }
}
