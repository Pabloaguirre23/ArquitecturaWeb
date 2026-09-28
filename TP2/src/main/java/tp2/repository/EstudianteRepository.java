package tp2.repository;

import jakarta.persistence.EntityManager;
import tp2.dto.CarreraInscriptosDTO;
import tp2.entity.Carrera;
import tp2.entity.Estudiante;
import tp2.entity.EstudianteCarrera;

import java.util.List;

/**
 * Consultas del Ejercicio Integrador, punto 2 (todo en JPQL).
 * Escrituras con rollback; lecturas sin transacción (JPA las permite).
 */
public class EstudianteRepository {

    private final EntityManager em;

    public EstudianteRepository(EntityManager em) {
        this.em = em;
    }

    // a) dar de alta un estudiante
    public void darDeAlta(Estudiante e) {
        try {
            em.getTransaction().begin();
            em.persist(e);
            em.getTransaction().commit();
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
    }

    // b) matricular un estudiante en una carrera (id autogenerado max+1)
    // TODO integrante A: abrir transacción con rollback, calcular
    //   SELECT COALESCE(MAX(ec.id), 0) FROM EstudianteCarrera ec,
    //   crear el EstudianteCarrera, persistirlo y agregarlo a ambos lados
    //   (estudiante.getCarreras().add(...) y carrera.getInscriptos().add(...)).
    public EstudianteCarrera matricular(Estudiante estudiante, Carrera carrera, int inscripcion, Integer graduacion, int antiguedad) {
        try {
            em.getTransaction().begin();
            Integer max = em.createQuery("SELECT COALESCE(MAX(ec.id), 0) FROM EstudianteCarrera ec", Integer.class)
                    .getSingleResult();
            EstudianteCarrera ec = new EstudianteCarrera(max + 1, estudiante, carrera, inscripcion, graduacion, antiguedad);
            em.persist(ec);
            estudiante.getCarreras().add(ec);
            carrera.getInscriptos().add(ec);
            em.getTransaction().commit();
            return ec;
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
    }

    // c) todos los estudiantes, ordenados por apellido y nombre
    public List<Estudiante> findAllOrdenados() {
        return em.createQuery("SELECT e FROM Estudiante e ORDER BY e.apellido ASC, e.nombre ASC", Estudiante.class)
                .getResultList();
    }

    // d) un estudiante por LU (null si no existe, en vez de lanzar NoResultException)
    // TODO integrante B: SELECT e FROM Estudiante e WHERE e.libretaUniversitaria = :lu
    //   + setParameter + getResultStream().findFirst().orElse(null).
    public Estudiante findByLibreta(int lu) {
        return em.createQuery("SELECT e FROM Estudiante e WHERE e.libretaUniversitaria = :lu", Estudiante.class)
                .setParameter("lu", lu)
                .getResultStream()
                .findFirst()

                .orElse(null);
    }

    public List<Estudiante> findByGenero(String genero) {
        return em.createQuery("SELECT e FROM Estudiante e WHERE e.genero = :genero ORDER BY e.apellido ASC, e.nombre ASC", Estudiante.class)
                .setParameter("genero", genero)
                .getResultList();
    }

    // e) estudiantes por género
    // TODO integrante B: SELECT e FROM Estudiante e WHERE e.genero = :genero ORDER BY e.apellido, e.nombre
    public List<Estudiante> findByGenero(String genero) {
        throw new UnsupportedOperationException("TODO integrante B");
    }

    // f) carreras con inscriptos, ordenadas por cantidad desc — DTO, no Object[]
    // TODO integrante C: SELECT NEW tp2.dto.CarreraInscriptosDTO(c.nombre, COUNT(ec))
    //   FROM Carrera c JOIN c.inscriptos ec GROUP BY c.id, c.nombre ORDER BY COUNT(ec) DESC

    public List<CarreraInscriptosDTO> findCarrerasConInscriptosOrdenadas() {
        return em.createQuery(
                        "SELECT NEW tp2.dto.CarreraInscriptosDTO(c.nombre, COUNT(ec)) " +
                                "FROM Carrera c JOIN c.inscriptos ec " +
                                "GROUP BY c.id, c.nombre " +
                                "ORDER BY COUNT(ec) DESC", CarreraInscriptosDTO.class)
                .getResultList();
    }

    // g) estudiantes de una carrera filtrados por ciudad
    // TODO integrante C: SELECT e FROM Estudiante e JOIN e.carreras ec
    //   WHERE ec.carrera.id = :idCarrera AND e.ciudad = :ciudad ORDER BY e.apellido, e.nombre


    public List<Estudiante> findEstudiantesPorCarreraYCiudad(int idCarrera, String ciudad) {
        return em.createQuery(
                        "SELECT e FROM Estudiante e JOIN e.carreras ec " +
                                "WHERE ec.carrera.id = :idCarrera AND e.ciudad = :ciudad " +
                                "ORDER BY e.apellido ASC, e.nombre ASC", Estudiante.class)
                .setParameter("idCarrera", idCarrera)
                .setParameter("ciudad", ciudad)
                .getResultList();
    }



    // Helper usado por el cargador CSV
    public Estudiante findByDni(int dni) {
        return em.find(Estudiante.class, dni);
    }
}
