package tp2.repository;

import com.opencsv.CSVReader;
import jakarta.persistence.EntityManager;
import tp2.dto.ReporteCarreraDTO;
import tp2.modelo.Carrera;
import tp2.factory.JPAUtil;
import tp2.modelo.Estudiante;

import java.io.FileReader;
import java.util.List;

public class CarreraRepositoryImpl implements CarreraRepository {

    @Override
    public void insertarCarrerasCSV(String rutaArchivo) {
        EntityManager em = JPAUtil.getEntityManager();
        try (CSVReader reader = new CSVReader(new FileReader(rutaArchivo))) {
            String[] linea;
            reader.readNext(); // salta cabecera

            em.getTransaction().begin();

            while ((linea = reader.readNext()) != null) {
                int id = Integer.parseInt(linea[0].trim());
                String nombre = linea[1].trim();
                int duracion = Integer.parseInt(linea[2].trim());

                Carrera carrera = new Carrera(id, nombre);
                carrera.setDuracion(duracion);

                em.persist(carrera);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    @Override
    public List<Carrera> getCarrerasConEstudiantesInscriptos() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT c FROM Carrera c JOIN c.inscripciones i GROUP BY c HAVING COUNT(i) > 0 ORDER BY COUNT(i) DESC";
            return em.createQuery(jpql, Carrera.class).getResultList();
        } finally {
            em.close();
        }
    }


    @Override
    public List<Estudiante> getEstudiantesPorCarreraYCiudad(int carreraId, String ciudad) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT i.estudiante FROM Inscripcion i " +
                    "WHERE i.carrera.id = :carreraId AND i.estudiante.ciudadResidencia = :ciudad";

            return em.createQuery(jpql, Estudiante.class)
                    .setParameter("carreraId", carreraId)
                    .setParameter("ciudad", ciudad)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    @Override
    public List<ReporteCarreraDTO> getReporteCarreras() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new tp2.dto.ReporteCarreraDTO(" +
                    "c.nombre, " +
                    "i.inscripcion, " +
                    "COUNT(i), " +
                    "SUM(CASE WHEN i.graduacion > 0 THEN 1 ELSE 0 END)) " +
                    "FROM Inscripcion i JOIN i.carrera c " +
                    "GROUP BY c.nombre, i.inscripcion " +
                    "ORDER BY c.nombre ASC, i.inscripcion ASC";

            return em.createQuery(jpql, ReporteCarreraDTO.class).getResultList();
        } finally {
            em.close();
        }
    }

}