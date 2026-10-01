package tp2.repository;

import tp2.dto.ReporteCarreraDTO;
import tp2.modelo.Carrera;
import tp2.modelo.Estudiante;

import java.util.List;

public interface CarreraRepository {

    void insertarCarrerasCSV(String rutaArchivo);

    List<Carrera> getCarrerasConEstudiantesInscriptos();

    List<Estudiante> getEstudiantesPorCarreraYCiudad(int carreraId, String ciudad);

    List<ReporteCarreraDTO> getReporteCarreras();
}
