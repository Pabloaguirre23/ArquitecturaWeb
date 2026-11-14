package ar.edu.unicen.ticketya.recommendation.web;

import java.util.UUID;

/**
 * Identidad propagada por el gateway. En la Entrega 2 el JWT se valida en el
 * borde y el gateway sigue enviando X-User-Id y X-User-Role a los servicios.
 */
public record RequestIdentity(UUID userId, String role) {

    public static RequestIdentity require(String rawUserId, String rawRole) {
        if (rawUserId == null || rawUserId.isBlank() || rawRole == null || rawRole.isBlank()) {
            throw ApiException.unauthorized("Faltan los encabezados X-User-Id y X-User-Role");
        }
        try {
            return new RequestIdentity(UUID.fromString(rawUserId), rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("X-User-Id debe ser un UUID válido");
        }
    }

    public void requireRole(String expectedRole) {
        if (!expectedRole.equals(role)) {
            throw ApiException.forbidden("FORBIDDEN",
                    "El rol " + role + " no puede ejecutar esta operación");
        }
    }
}
