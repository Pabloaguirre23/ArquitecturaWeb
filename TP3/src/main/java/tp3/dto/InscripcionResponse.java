package tp3.dto;

import tp3.modelo.Inscripcion;

public record InscripcionResponse(
        Long id,
        String carrera,
        String fechaInscripcion,
        Boolean egresado,
        String fechaEgreso
) {
    public static InscripcionResponse from(Inscripcion ec) {
        return new InscripcionResponse(
                ec.getId(),
                ec.getCarrera().getNombre(),
                ec.getFechaInscripcion() != null ? ec.getFechaInscripcion().toString() : null,
                ec.getEgresado(),
                ec.getFechaEgreso() != null ? ec.getFechaEgreso().toString() : null
        );
    }
}