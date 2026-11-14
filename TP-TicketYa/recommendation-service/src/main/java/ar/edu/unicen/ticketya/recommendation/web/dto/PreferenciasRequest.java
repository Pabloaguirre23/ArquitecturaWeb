package ar.edu.unicen.ticketya.recommendation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Preferencias que declara un cliente. Si envía clienteId debe coincidir con
 * la identidad autenticada; sirve para detectar intentos de escribir otro perfil.
 */
public record PreferenciasRequest(
        @Size(max = 10, message = "se admiten hasta 10 tipos de evento")
        List<@NotBlank(message = "el tipo no puede estar vacío")
             @Size(max = 40, message = "el tipo no puede superar 40 caracteres") String> tipos,

        @Size(max = 20, message = "se admiten hasta 20 artistas")
        List<@NotBlank(message = "el artista no puede estar vacío")
             @Size(max = 120, message = "el artista no puede superar 120 caracteres") String> artistas,

        @NotBlank(message = "la ciudad es obligatoria")
        @Size(max = 80, message = "la ciudad no puede superar 80 caracteres")
        String ciudad,

        UUID clienteId) {
}
