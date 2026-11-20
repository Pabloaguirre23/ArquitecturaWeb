package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Payload de {@code orden.reintegrada} (docs/eventos.md §3.2): la saga de
 * compra compensó la orden y hay que avisar el reintegro al cliente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenReintegradaPayload(
        String ordenId,
        String clienteId,
        String eventoId,
        BigDecimal monto,
        String motivo) {
}
