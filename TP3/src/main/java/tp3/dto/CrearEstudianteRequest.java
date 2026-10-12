package tp3.dto;

import tp3.modelo.Genero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearEstudianteRequest(
        @NotBlank(message = "nombres es obligatorio")
        String nombres,

        @NotBlank(message = "apellido es obligatorio")
        String apellido,

        @NotNull(message = "edad es obligatoria")
        @Min(value = 1, message = "edad debe ser mayor a 0")
        @Max(value = 120, message = "edad debe ser menor o igual a 120")
        Integer edad,

        @NotNull(message = "genero es obligatorio")
        Genero genero,

        @NotBlank(message = "documento es obligatorio")
        String documento,

        @NotBlank(message = "ciudadResidencia es obligatoria")
        String ciudadResidencia,

        @NotNull(message = "libretaUniversitaria es obligatoria")
        @Positive(message = "libretaUniversitaria debe ser positiva")
        Long libretaUniversitaria
) {
}