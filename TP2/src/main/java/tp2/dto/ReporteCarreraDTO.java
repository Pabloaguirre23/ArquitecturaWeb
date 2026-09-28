package tp2.dto;

/**
 * Punto 3: una fila por (carrera, año).
 * inscriptos = matriculados ese año; egresados = graduados ese año.
 * El reporte final ordena por carrera asc y año asc (ver repository).
 */
public record ReporteCarreraDTO(String carrera, int anio, long inscriptos, long egresados) {}
