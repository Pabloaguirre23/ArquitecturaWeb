package tp3.repository;

import tp3.modelo.Estudiante;
import tp3.modelo.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    Optional<Inscripcion> findByEstudianteIdAndCarreraId(Long estudianteId, Long carreraId);

    // (g) estudiantes de una determinada carrera, filtrados por ciudad de residencia
    @Query("""
            select ec.estudiante
            from Inscripcion ec
            where ec.carrera.id = :carreraId
              and lower(ec.estudiante.ciudadResidencia) = lower(:ciudad)
            order by ec.estudiante.apellido asc, ec.estudiante.nombres asc
            """)
    List<Estudiante> findEstudiantesDeCarreraEnCiudad(@Param("carreraId") Long carreraId,
                                                      @Param("ciudad") String ciudad);

    // (h) inscriptos por carrera y anio de inscripcion
    @Query("""
            select ec.carrera.nombre as nombreCarrera,
                   function('year', ec.fechaInscripcion) as anio,
                   count(ec) as cantidad
            from Inscripcion ec
            group by ec.carrera.nombre, function('year', ec.fechaInscripcion)
            order by ec.carrera.nombre asc, function('year', ec.fechaInscripcion) asc
            """)
    List<InscriptosPorCarreraYAnio> reporteInscriptosPorCarreraYAnio();

    // (h) egresados por carrera y anio de egreso
    @Query("""
            select ec.carrera.nombre as nombreCarrera,
                   function('year', ec.fechaEgreso) as anio,
                   count(ec) as cantidad
            from Inscripcion ec
            where ec.egresado = true and ec.fechaEgreso is not null
            group by ec.carrera.nombre, function('year', ec.fechaEgreso)
            order by ec.carrera.nombre asc, function('year', ec.fechaEgreso) asc
            """)
    List<EgresadosPorCarreraYAnio> reporteEgresadosPorCarreraYAnio();

    interface InscriptosPorCarreraYAnio {
        String getNombreCarrera();

        Integer getAnio();

        Long getCantidad();
    }

    interface EgresadosPorCarreraYAnio {
        String getNombreCarrera();

        Integer getAnio();

        Long getCantidad();
    }
}