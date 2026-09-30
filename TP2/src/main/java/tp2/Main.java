package tp2;

import jakarta.persistence.EntityManager;
import tp2.entity.Carrera;
import tp2.entity.Estudiante;
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

            System.out.println("=== c) todos ordenados ===");
            estudiantes.findAllOrdenados().stream().limit(10).forEach(System.out::println);

            run("b) matricular", () -> {
                Estudiante e = estudiantes.findByDni(71779527);
                Carrera c = carreras.findById(1);
                System.out.println(estudiantes.matricular(e, c, 2024, null, 1));
            });
            run("d) por LU 34978", () -> System.out.println(estudiantes.findByLibreta(34978)));
            run("e) genero Male (tope 5)", () -> estudiantes.findByGenero("Male").stream().limit(5).forEach(System.out::println));
            run("f) carreras con inscriptos", () -> estudiantes.findCarrerasConInscriptosOrdenadas().forEach(System.out::println));
            run("g) carrera 1 en Tandil", () -> estudiantes.findEstudiantesPorCarreraYCiudad(1, "Tandil").forEach(System.out::println));
            run("3) reporte por anio", () -> carreras.reporteCarrerasPorAnio().forEach(System.out::println));
        } finally {
            if (em.isOpen()) em.close();
            JPAUtil.close();
        }
    }

    /** Ejecuta una consulta; si el método sigue TODO, lo informa y sigue con la próxima. */
    private static void run(String titulo, Runnable consulta) {
        System.out.println("=== " + titulo + " ===");
        try {
            consulta.run();
        } catch (UnsupportedOperationException ex) {
            System.out.println("(pendiente: " + ex.getMessage() + ")");
        }
    }
}
