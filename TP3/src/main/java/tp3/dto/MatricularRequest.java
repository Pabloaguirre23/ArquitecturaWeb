package tp3.dto;

import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record MatricularRequest(
        @PastOrPresent(message = "fechaInscripcion no puede ser futura")
        LocalDate fechaInscripcion
) {
}