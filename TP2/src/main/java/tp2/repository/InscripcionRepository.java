package tp2.repository;

import tp2.modelo.Inscripcion;

public interface InscripcionRepository {

    void insertarInscripcionCSV(String rutaArchivo);

    Inscripcion matricular(int idEstudiante, int idCarrera);
}
