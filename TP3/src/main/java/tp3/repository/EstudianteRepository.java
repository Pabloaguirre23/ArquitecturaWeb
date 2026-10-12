package tp3.repository;

import tp3.modelo.Estudiante;
import tp3.modelo.Genero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    Optional<Estudiante> findByLibretaUniversitaria(Long libretaUniversitaria);

    // (c) ordenamiento simple por apellido y nombres
    List<Estudiante> findAllByOrderByApellidoAscNombresAsc();

    // (c) ordenamiento simple por numero de libreta universitaria
    List<Estudiante> findAllByOrderByLibretaUniversitariaAsc();

    // (e) todos los estudiantes por genero
    List<Estudiante> findByGeneroOrderByApellidoAscNombresAsc(Genero genero);

    long countByDocumento(String documento);

    long countByLibretaUniversitaria(Long libretaUniversitaria);
}