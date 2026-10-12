package tp3.dto;

import java.util.List;

public record ReporteCarreraResponse(
        String carrera,
        List<AnioResumenResponse> anios
) {
}