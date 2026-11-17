package ar.edu.unicen.ticketya.queue.web;

import java.util.UUID;

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
            throw ApiException.forbidden("El rol " + role + " no puede ejecutar esta operación");
        }
    }
}
