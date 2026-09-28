package tp2.repository;

import jakarta.persistence.EntityManager;
import tp2.dto.ReporteCarreraDTO;
import tp2.entity.Carrera;

import java.util.List;

public class CarreraRepository {

    private final EntityManager em;

    public CarreraRepository(EntityManager em) {
        this.em = em;
    }

    public void guardar(Carrera c) {
        try {
            em.getTransaction().begin();
            em.merge(c);
            em.getTransaction().commit();
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
    }

    public Carrera findById(int id) {
        return em.find(Carrera.class, id);
    }

    public List<Carrera> findAllOrdenadasPorNombre() {
        return em.createQuery("SELECT c FROM Carrera c ORDER BY c.nombre ASC", Carrera.class)
                .getResultList();
    }

    /**
     * Punto 3: por cada (carrera, año de inscripción): inscriptos y egresados.
     * Supuesto documentado: el "año" es el de inscripción; egresados = de esos
     * inscriptos, los que tienen graduación no nula. Todo agregado en JPQL
     * (SUM+CASE), ordenado por carrera asc y año asc como pide la consigna.
     *
     * TODO integrante D: SELECT NEW tp2.dto.ReporteCarreraDTO(c.nombre, ec.inscripcion,
     *   COUNT(ec), SUM(CASE WHEN ec.graduacion IS NOT NULL THEN 1 ELSE 0 END))
     *   FROM EstudianteCarrera ec JOIN ec.carrera c
     *   GROUP BY c.nombre, ec.inscripcion ORDER BY c.nombre ASC, ec.inscripcion ASC
     */
    public List<ReporteCarreraDTO> reporteCarrerasPorAnio() {
        throw new UnsupportedOperationException("TODO integrante D");
    }
}
