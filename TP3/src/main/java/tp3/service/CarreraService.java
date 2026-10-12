package tp3.service;

import tp3.repository.CarreraRepository;
import tp3.repository.InscripcionRepository;
import tp3.dto.AnioResumenResponse;
import tp3.dto.CarreraConInscriptosResponse;
import tp3.dto.ReporteCarreraResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CarreraService {

    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;

    public CarreraService(CarreraRepository carreraRepository,
                          InscripcionRepository inscripcionRepository) {
        this.carreraRepository = carreraRepository;
        this.inscripcionRepository = inscripcionRepository;
    }

    // (f) carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
    @Transactional(readOnly = true)
    public List<CarreraConInscriptosResponse> carrerasConInscriptos() {
        return carreraRepository.findCarrerasConInscriptos().stream()
                .map(CarreraConInscriptosResponse::from)
                .toList();
    }

    // (h) reporte de carreras: inscriptos y egresados por anio.
    // Carreras ordenadas alfabeticamente y anios cronologicamente.
    @Transactional(readOnly = true)
    public List<ReporteCarreraResponse> reporteCarreras() {
        Map<String, Map<Integer, long[]>> porCarrera = new LinkedHashMap<>();

        for (InscripcionRepository.InscriptosPorCarreraYAnio fila
                : inscripcionRepository.reporteInscriptosPorCarreraYAnio()) {
            porCarrera.computeIfAbsent(fila.getNombreCarrera(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(fila.getAnio(), k -> new long[2])[0] += fila.getCantidad();
        }

        for (InscripcionRepository.EgresadosPorCarreraYAnio fila
                : inscripcionRepository.reporteEgresadosPorCarreraYAnio()) {
            porCarrera.computeIfAbsent(fila.getNombreCarrera(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(fila.getAnio(), k -> new long[2])[1] += fila.getCantidad();
        }

        List<ReporteCarreraResponse> resultado = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, long[]>> carrera : porCarrera.entrySet()) {
            List<AnioResumenResponse> anios = carrera.getValue().entrySet().stream()
                    .map(e -> new AnioResumenResponse(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .toList();
            resultado.add(new ReporteCarreraResponse(carrera.getKey(), anios));
        }
        return resultado;
    }
}