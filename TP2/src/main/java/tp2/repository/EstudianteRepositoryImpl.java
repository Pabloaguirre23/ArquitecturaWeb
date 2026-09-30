package repository;

import com.opencsv.CSVReader;
import dto.EstudianteDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import modelo.Estudiante;
import factory.JPAUtil;

import java.io.FileReader;
import java.util.List;

public class EstudianteRepositoryImpl implements EstudianteRepository {

    @Override
    public void insertarEstudiantesCSV(String rutaArchivo) {
        EntityManager em = JPAUtil.getEntityManager();
        try (CSVReader reader = new CSVReader(new FileReader(rutaArchivo))) {
            String[] linea;
            reader.readNext(); // salta cabecera

            em.getTransaction().begin();

            while ((linea = reader.readNext()) != null) {
                int documento = Integer.parseInt(linea[0].trim());
                String nombre = linea[1].trim();
                String apellido = linea[2].trim();
                int edad = Integer.parseInt(linea[3].trim());
                String genero = linea[4];
                String ciudad = linea[5].trim();
                int libreta = Integer.parseInt(linea[6].trim());

                Estudiante estudiante = new Estudiante();
                estudiante.setNombre(nombre);
                estudiante.setApellido(apellido);
                estudiante.setEdad(edad);
                estudiante.setGenero(genero);
                estudiante.setNumeroDocumento(documento);
                estudiante.setCiudadResidencia(ciudad);
                estudiante.setNumeroLibreta(libreta);
                // genero: no viene en este CSV, queda sin asignar

                em.persist(estudiante);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    public Estudiante alta(Estudiante e) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(e);
            em.getTransaction().commit();
            return e;
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }

    public List<EstudianteDTO> todosLosEstudiantesOrdenados(String atributo, String orden) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            boolean ordValido = false;
            String jpql = "SELECT new dto.EstudianteDTO(e.numeroDocumento, e.nombre, e.apellido, e.edad, e.genero, e.ciudadResidencia, e.numeroLibreta) " +
                    "FROM Estudiante e ";

            switch (atributo) {
                case "id":
                    jpql += "ORDER BY e.id ";
                    ordValido = true;
                    break;
                case "DNI":
                    jpql += "ORDER BY e.numeroDocumento ";
                    ordValido = true;
                    break;
                case "nombre":
                    jpql += "ORDER BY e.nombre ";
                    ordValido = true;
                    break;
                case "apellido":
                    jpql += "ORDER BY e.apellido ";
                    ordValido = true;
                    break;
                case "edad":
                    jpql += "ORDER BY e.edad ";
                    ordValido = true;
                    break;
                case "LU":
                    jpql += "ORDER BY e.numeroLibreta ";
                    ordValido = true;
                    break;
            }

            if (ordValido) {
                switch (orden) {
                    case "ASC":
                        jpql += "ASC";
                        break;
                    case "DESC":
                        jpql += "DESC";
                        break;
                }
            }

            return em.createQuery(jpql, EstudianteDTO.class).getResultList();
        } finally {
            em.close();
        }
    }

    public EstudianteDTO getEstudianteByLU(int numeroLibreta) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                            "SELECT new dto.EstudianteDTO(e.numeroDocumento, e.nombre, e.apellido, e.edad, e.genero, e.ciudadResidencia, e.numeroLibreta) " +
                                    "FROM Estudiante e WHERE e.numeroLibreta = :LU", EstudianteDTO.class)
                    .setParameter("LU", numeroLibreta)
                    .getSingleResult();
        } catch (NoResultException ex) {
            throw new IllegalStateException("No existe un estudiante con LU=" + numeroLibreta);
        } finally {
            em.close();
        }
    }

    public List<EstudianteDTO> getEstudiantesByGenero(String g) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new dto.EstudianteDTO(e.numeroDocumento, e.nombre, e.apellido, e.edad, e.genero, e.ciudadResidencia, e.numeroLibreta) " +
                    "FROM Estudiante e";

            TypedQuery<EstudianteDTO> query;

            if ("masculino".equals(g) || "femenino".equals(g)) {
                query = em.createQuery(jpql + " WHERE e.genero = :genero", EstudianteDTO.class)
                        .setParameter("genero", g);
            } else {
                query = em.createQuery(jpql, EstudianteDTO.class);
            }

            return query.getResultList();
        } finally {
            em.close();
        }
    }
}