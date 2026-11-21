package ar.edu.unicen.ticketya.orders.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Solicitud de validación de un QR de entrada al evento")
public record AccessValidationRequest(
        @NotNull @Schema(description = "QR de la entrada", example = "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e")
        UUID qrCode,
        @NotNull @Schema(description = "Evento al que se intenta ingresar",
                example = "d0000000-0000-4000-8000-000000000001")
        UUID eventoId) {
}
