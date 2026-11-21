package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pasarela mock de la Entrega 1 (tarea 4.3). El token define el resultado:
 * APROBADO, RECHAZADO o LENTO (timeout simulado). La clave de idempotencia se
 * recibe y se ignora porque el mock no mueve dinero.
 *
 * <p>Desde la tarea 6.6 los comandos pasan por un circuit breaker y un retry
 * acotado. El reintento reutiliza la misma clave de idempotencia: una pasarela
 * real no debe cobrar dos veces el mismo comando.</p>
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final String CIRCUITO = "pagos";
    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "cobrarFallback")
    @Retry(name = CIRCUITO)
    public PaymentResult cobrar(UUID ordenId, BigDecimal monto, MedioPago medioPago, String token,
                                String idempotencyKey) {
        String resultado = token == null || token.isBlank() ? "APROBADO" : token.trim().toUpperCase();
        return switch (resultado) {
            case "APROBADO" -> new PaymentResult(EstadoPago.APROBADO, "mock-" + ordenId);
            case "RECHAZADO" -> new PaymentResult(EstadoPago.RECHAZADO, "mock-" + ordenId);
            case "LENTO" -> throw new PaymentGatewayException("La pasarela de pagos no respondió a tiempo");
            default -> throw new PaymentGatewayException("Resultado de pasarela desconocido: " + token);
        };
    }

    @Override
    @CircuitBreaker(name = CIRCUITO, fallbackMethod = "reintegrarFallback")
    @Retry(name = CIRCUITO)
    public void reintegrar(UUID pagoId, BigDecimal monto, String idempotencyKey) {
        // El mock no mueve dinero: el estado del reintegro/pago es la evidencia.
    }

    @SuppressWarnings("unused")
    private PaymentResult cobrarFallback(UUID ordenId, BigDecimal monto, MedioPago medioPago, String token,
                                         String idempotencyKey, Throwable causa) {
        log.warn("Fallback de pagos: no se pudo cobrar la orden {}: {}", ordenId, causa.toString());
        throw new PaymentGatewayException("La pasarela de pagos no respondió; la compra no se completó");
    }

    @SuppressWarnings("unused")
    private void reintegrarFallback(UUID pagoId, BigDecimal monto, String idempotencyKey, Throwable causa) {
        log.warn("Fallback de pagos: no se pudo reintegrar el pago {}: {}", pagoId, causa.toString());
        throw new PaymentGatewayException("La pasarela de pagos no respondió; el reintegro queda pendiente");
    }
}
