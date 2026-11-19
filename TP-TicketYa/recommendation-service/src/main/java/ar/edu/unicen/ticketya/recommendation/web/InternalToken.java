package ar.edu.unicen.ticketya.recommendation.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protege los endpoints /internal/** con el token compartido entre servicios.
 * El gateway elimina el encabezado del tráfico externo (tarea 2.3).
 */
@Component
public class InternalToken {

    private final String expected;

    public InternalToken(@Value("${recommendation.internal-token}") String expected) {
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
