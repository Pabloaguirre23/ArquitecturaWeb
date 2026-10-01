package tp2.repository;

import tp2.dto.EstudianteDTO;
import tp2.modelo.Estudiante;

import java.util.List;

public interface EstudianteRepository {

    void insertarEstudiantesCSV(String rutaArchivo);

    Estudiante alta(Estudiante e);

    List<EstudianteDTO> todosLosEstudiantesOrdenados(String atributo, String orden);

    EstudianteDTO getEstudianteByLU(int numeroLibreta);

    List<EstudianteDTO> getEstudiantesByGenero(String genero);
}
