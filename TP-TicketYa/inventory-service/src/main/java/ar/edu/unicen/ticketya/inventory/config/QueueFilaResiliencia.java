package ar.edu.unicen.ticketya.inventory.config;

import ar.edu.unicen.ticketya.inventory.servicio.QueueFilaClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QueueFilaResiliencia {

    private static final Logger log = LoggerFactory.getLogger(QueueFilaResiliencia.class);

    public QueueFilaResiliencia(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
        circuitBreakers.circuitBreaker(QueueFilaClient.CIRCUITO).getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuito de queue cambio de estado: {}", event.getStateTransition()));
        retries.retry(QueueFilaClient.CIRCUITO).getEventPublisher()
                .onRetry(event -> log.warn(
                        "Reintento {} hacia queue: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().toString()))
                .onError(event -> log.warn(
                        "Se agotaron los reintentos hacia queue: {}",
                        event.getLastThrowable().toString()));
    }
}
