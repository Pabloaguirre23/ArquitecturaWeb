package ar.edu.unicen.ticketya.orders.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege /internal/** con el token compartido entre servicios.
 * El gateway no publica esas rutas.
 */
public class InternalToken {

    private final String expected;

    public InternalToken(String expected) {
        this.expected = expected;
    }

    public void require(String provided) {
        if (provided == null || expected == null || expected.isBlank()
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Falta o es inválido el encabezado X-Internal-Token");
        }
    }
}
