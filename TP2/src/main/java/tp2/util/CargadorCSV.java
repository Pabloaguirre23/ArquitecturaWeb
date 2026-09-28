package tp2.util;

import jakarta.persistence.EntityManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import tp2.entity.Carrera;
import tp2.entity.Estudiante;
import tp2.entity.EstudianteCarrera;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;

/**
 * Carga los 3 CSV del integrador a la base.
 * TODO integrante D: completar matricular() y probar carga completa.
 *
 * NOTA datos sucios: estudianteCarrera.csv trae id_carrera (15, 11, 9...)
 * que NO existen en carreras.csv (solo 1-4). Esos registros se saltean
 * con un warning (no se puede matricular a carrera inexistente).
 */
public final class CargadorCSV {
    private CargadorCSV() {}

    public static void cargarTodo(EntityManager em, Path baseDir) throws Exception {
        cargarCarreras(em, baseDir.resolve("carreras.csv"));
        cargarEstudiantes(em, baseDir.resolve("estudiantes.csv"));
        cargarMatriculas(em, baseDir.resolve("estudianteCarrera.csv"));
    }

    public static void cargarCarreras(EntityManager em, Path csv) throws Exception {
        try (Reader r = new FileReader(csv.toFile());
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(r)) {
            try {
                em.getTransaction().begin();
                for (CSVRecord rec : p) {
                    int id = Integer.parseInt(rec.get("id_carrera").trim());
                    if (em.find(Carrera.class, id) == null) {
                        em.persist(new Carrera(id, rec.get("carrera").trim(),
                                Integer.parseInt(rec.get("duracion").trim())));
                    }
                }
                em.getTransaction().commit();
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    public static void cargarEstudiantes(EntityManager em, Path csv) throws Exception {
        try (Reader r = new FileReader(csv.toFile());
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(r)) {
            try {
                em.getTransaction().begin();
                for (CSVRecord rec : p) {
                    int dni = Integer.parseInt(rec.get("DNI").trim());
                    if (em.find(Estudiante.class, dni) == null) {
                        em.persist(new Estudiante(
                                dni,
                                rec.get("nombre").trim(),
                                rec.get("apellido").trim(),
                                Integer.parseInt(rec.get("edad").trim()),
                                rec.get("genero").trim(),
                                rec.get("ciudad").trim(),
                                Integer.parseInt(rec.get("LU").trim())));
                    }
                }
                em.getTransaction().commit();
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    public static void cargarMatriculas(EntityManager em, Path csv) throws Exception {
        try (Reader r = new FileReader(csv.toFile());
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(r)) {
            try {
                em.getTransaction().begin();
                for (CSVRecord rec : p) {
                    int id = Integer.parseInt(rec.get("id").trim());
                    if (em.find(EstudianteCarrera.class, id) != null) continue;
                    int dni = Integer.parseInt(rec.get("id_estudiante").trim());
                    int idCarrera = Integer.parseInt(rec.get("id_carrera").trim());
                    Estudiante e = em.find(Estudiante.class, dni);
                    Carrera c = em.find(Carrera.class, idCarrera);
                    if (e == null || c == null) {
                        System.out.println("[WARN] matrícula id=" + id + " salteada (estudiante o carrera inexistente: dni=" + dni + ", carrera=" + idCarrera + ")");
                        continue;
                    }
                    int insc = Integer.parseInt(rec.get("inscripcion").trim());
                    int gradRaw = Integer.parseInt(rec.get("graduacion").trim());
                    Integer grad = (gradRaw == 0) ? null : gradRaw;
                    int antig = Integer.parseInt(rec.get("antiguedad").trim());
                    EstudianteCarrera ec = new EstudianteCarrera(id, e, c, insc, grad, antig);
                    em.persist(ec);
                    e.getCarreras().add(ec);
                    c.getInscriptos().add(ec);
                }
                em.getTransaction().commit();
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }
}
