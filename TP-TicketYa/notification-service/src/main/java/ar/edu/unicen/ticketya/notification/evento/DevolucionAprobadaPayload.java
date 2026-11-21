package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Payload de {@code devolucion.aprobada} (docs/eventos.md §3.4): se aprobó
 * la devolución de una entrada y hay que confirmar el reintegro al cliente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DevolucionAprobadaPayload(
        String devolucionId,
        String ordenId,
        String entradaId,
        String clienteId,
        BigDecimal monto) {
}
