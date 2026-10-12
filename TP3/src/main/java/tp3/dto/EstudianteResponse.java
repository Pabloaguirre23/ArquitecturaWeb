package tp3.dto;

import tp3.modelo.Estudiante;

import java.util.List;

public record EstudianteResponse(
        Long id,
        String nombres,
        String apellido,
        Integer edad,
        String genero,
        String documento,
        String ciudadResidencia,
        Long libretaUniversitaria,
        List<InscripcionResponse> inscripciones
) {
    public static EstudianteResponse from(Estudiante e) {
        List<InscripcionResponse> inscripciones = e.getInscripciones().stream()
                .map(InscripcionResponse::from)
                .toList();
        return new EstudianteResponse(
                e.getId(),
                e.getNombres(),
                e.getApellido(),
                e.getEdad(),
                e.getGenero().name(),
                e.getDocumento(),
                e.getCiudadResidencia(),
                e.getLibretaUniversitaria(),
                inscripciones
        );
    }
}