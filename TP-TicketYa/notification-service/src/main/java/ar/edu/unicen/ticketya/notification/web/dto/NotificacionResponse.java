package ar.edu.unicen.ticketya.notification.web.dto;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacionResponse(
        UUID id,
        UUID eventoId,
        String tipo,
        String asunto,
        String contenido,
        EstadoNotificacion estado,
        int intentos,
        @Schema(nullable = true) OffsetDateTime proximoReintento,
        OffsetDateTime createdAt,
        @Schema(nullable = true) OffsetDateTime enviadaAt) {
}
