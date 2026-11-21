package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resultado de la validación de una entrada")
public record AccessValidationResponse(
        UUID entradaId,
        UUID ordenId,
        UUID eventoId,
        UUID sectorId,
        String estado,
        OffsetDateTime usedAt,
        UUID usedBy) {

    public static AccessValidationResponse from(Entrada entrada, OffsetDateTime ahora, UUID usuarioId) {
        return new AccessValidationResponse(
                entrada.getId(),
                entrada.getOrdenId(),
                entrada.getEventoId(),
                entrada.getSectorId(),
                EstadoEntrada.UTILIZADA.name(),
                ahora,
                usuarioId);
    }
}
