package tp2;

import jakarta.persistence.EntityManager;
import tp2.repository.CarreraRepository;
import tp2.repository.EstudianteRepository;
import tp2.util.CargadorCSV;
import tp2.util.JPAUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String pu = args.length > 0 ? args[0] : "derbyPU";
        Path base = args.length > 1 ? Paths.get(args[1]) : Paths.get("TP2");
        if (!base.resolve("estudiantes.csv").toFile().exists()
                && Paths.get("estudiantes.csv").toFile().exists()) {
            base = Paths.get(".");
        }

        EntityManager em = JPAUtil.getEntityManager(pu);
        try {
            CargadorCSV.cargarTodo(em, base);
            System.out.println("Carga OK desde " + base.toAbsolutePath());

            EstudianteRepository estudiantes = new EstudianteRepository(em);
            CarreraRepository carreras = new CarreraRepository(em);

            System.out.println("=== c) todos ordenados (base, ya funciona) ===");
            estudiantes.findAllOrdenados().stream().limit(10).forEach(System.out::println);

            // Descomentar a medida que cada integrante implementa su TODO en su rama:
            // --- Integrante A: matricular() ---
            // --- Integrante B: findByLibreta(34978), findByGenero("Male") ---
            // --- Integrante C: findCarrerasConInscriptosOrdenadas(), findEstudiantesPorCarreraYCiudad(1, "Tandil") ---
            // --- Integrante D: carreras.reporteCarrerasPorAnio() ---
        } finally {
            if (em.isOpen()) em.close();
            JPAUtil.close();
        }
    }
}
