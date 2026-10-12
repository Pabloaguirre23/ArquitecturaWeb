package tp3.repository;

import tp3.modelo.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CarreraRepository extends JpaRepository<Carrera, Long> {

    Optional<Carrera> findByNombre(String nombre);

    // (f) carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos (mayor a menor)
    @Query("""
            select c.id as id,
                   c.nombre as nombre,
                   count(ec) as cantidad
            from Carrera c
            join c.inscripciones ec
            group by c.id, c.nombre
            order by count(ec) desc, c.nombre asc
            """)
    List<CarreraConInscriptos> findCarrerasConInscriptos();

    interface CarreraConInscriptos {
        Long getId();

        String getNombre();

        Long getCantidad();
    }
}