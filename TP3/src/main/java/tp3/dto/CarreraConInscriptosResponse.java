package tp3.dto;

import tp3.repository.CarreraRepository.CarreraConInscriptos;

public record CarreraConInscriptosResponse(
        Long id,
        String nombre,
        Long cantidadInscriptos
) {
    public static CarreraConInscriptosResponse from(CarreraConInscriptos proy) {
        return new CarreraConInscriptosResponse(proy.getId(), proy.getNombre(), proy.getCantidad());
    }
}