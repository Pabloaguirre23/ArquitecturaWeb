package tp2.repository;

import com.opencsv.CSVReader;
import jakarta.persistence.EntityManager;
import tp2.modelo.Carrera;
import tp2.factory.JPAUtil;

import java.io.FileReader;

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
}