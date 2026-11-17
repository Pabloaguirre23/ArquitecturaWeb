package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pasarela de pagos reemplazable (tarea 4.3). La implementación de la Entrega 1
 * es un mock controlado por el token que manda el cliente.
 *
 * <p>Todos los comandos llevan una clave de idempotencia estable (por ejemplo
 * {@code ordenId:PAGO_APROBADO} o {@code pagoId:REINTEGRO:COMPRA}) para que un
 * reintento no cobre ni reintegre dos veces.</p>
 */
public interface PaymentGateway {

    PaymentResult cobrar(UUID ordenId, BigDecimal monto, MedioPago medioPago, String token,
                         String idempotencyKey);

    /**
     * Solicita el reintegro de un pago por un monto específico. La clave de
     * idempotencia la construye el llamador a partir del pago y la entrada.
     */
    void reintegrar(UUID pagoId, BigDecimal monto, String idempotencyKey);

    record PaymentResult(EstadoPago estado, String externalId) {
    }
}
