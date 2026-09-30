package repository;

import com.opencsv.CSVReader;
import jakarta.persistence.EntityManager;
import modelo.Estudiante;
import modelo.Carrera;
import modelo.Inscripcion;
import factory.JPAUtil;
import java.time.Year;

import java.io.FileReader;

public class InscripcionRepositoryImpl implements InscripcionRepository {

    public void insertarInscripcionCSV(String rutaArchivo) {
        EntityManager em = JPAUtil.getEntityManager();
        try (CSVReader reader = new CSVReader(new FileReader(rutaArchivo))) {
            String[] linea;
            reader.readNext(); // salta cabecera

            em.getTransaction().begin();

            while ((linea = reader.readNext()) != null) {
                // linea[0] es el "id" propio del CSV — lo ignoramos,
                // porque Inscripcion.id es IDENTITY (autogenerado)
                int idEstudiante = Integer.parseInt(linea[1]);
                int idCarrera = Integer.parseInt(linea[2]);
                int anioInscripcion = Integer.parseInt(linea[3]);
                int anioGraduacion = Integer.parseInt(linea[4]);
                int antiguedad = Integer.parseInt(linea[5]);

                Estudiante estudianteRef = em.getReference(Estudiante.class, idEstudiante);
                Carrera carreraRef = em.getReference(Carrera.class, idCarrera);

                Inscripcion insc = new Inscripcion();
                insc.setEstudiante(estudianteRef);
                insc.setCarrera(carreraRef);
                insc.setInscripcion(anioInscripcion);
                insc.setGraduacion(anioGraduacion);
                insc.setAntiguedad(antiguedad);

                em.persist(insc);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    public Inscripcion matricular(int idEstudiante, int idCarrera) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Estudiante estudiante = em.find(Estudiante.class, idEstudiante);
            if (estudiante == null) {
                throw new IllegalStateException("No existe un estudiante con id=" + idEstudiante);
            }

            Carrera carrera = em.find(Carrera.class, idCarrera);
            if (carrera == null) {
                throw new IllegalStateException("No existe una carrera con id=" + idCarrera);
            }

            Long yaInscripto = em.createQuery(
                            "SELECT COUNT(i) FROM Inscripcion i WHERE i.estudiante = :est AND i.carrera = :car",
                            Long.class)
                    .setParameter("est", estudiante)
                    .setParameter("car", carrera)
                    .getSingleResult();

            if (yaInscripto > 0) {
                throw new IllegalStateException("El estudiante ya está inscripto en esa carrera");
            }

            int anioActual = Year.now().getValue();

            Inscripcion inscripcion = new Inscripcion(estudiante, carrera, anioActual, 0, 0);
            em.persist(inscripcion);

            estudiante.getInscripciones().add(inscripcion);
            carrera.getInscripciones().add(inscripcion);

            em.getTransaction().commit();
            return inscripcion;

        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }
}
