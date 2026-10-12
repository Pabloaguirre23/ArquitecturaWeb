package tp3.dto;

public record AnioResumenResponse(
        Integer anio,
        Long inscriptos,
        Long egresados
) {
}