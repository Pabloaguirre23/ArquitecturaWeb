package tp2;

import tp2.repository.CarreraRepository;
import tp2.repository.CarreraRepositoryImpl;
import tp2.repository.EstudianteRepository;
import tp2.repository.EstudianteRepositoryImpl;
import tp2.repository.InscripcionRepository;
import tp2.repository.InscripcionRepositoryImpl;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Path base = Paths.get("TP2/src/main/resources");
        if (!base.resolve("estudiantes.csv").toFile().exists()
                && Paths.get("src/main/resources/estudiantes.csv").toFile().exists()) {
            base = Paths.get("src/main/resources");
        }
        final Path recursos = base;

        // Apertura/cierre del EM administrado por cada repository (estilo del equipo).
        EstudianteRepository estudiantes = new EstudianteRepositoryImpl();
        CarreraRepository carreras = new CarreraRepositoryImpl();
        InscripcionRepository inscripciones = new InscripcionRepositoryImpl();

        run("carga carreras CSV", () -> carreras.insertarCarrerasCSV(recursos.resolve("carreras.csv").toString()));
        run("carga estudiantes CSV", () -> estudiantes.insertarEstudiantesCSV(recursos.resolve("estudiantes.csv").toString()));
        run("carga inscripciones CSV", () -> inscripciones.insertarInscripcionCSV(recursos.resolve("estudianteCarrera.csv").toString()));

        run("c) todos ordenados por apellido", () -> estudiantes.todosLosEstudiantesOrdenados("apellido", "ASC").forEach(System.out::println));
        run("d) por LU 34978", () -> System.out.println(estudiantes.getEstudianteByLU(34978)));
        run("e) por genero Male", () -> System.out.println("cantidad: " + estudiantes.getEstudiantesByGenero("Male").size()));
        run("f) carreras con inscriptos", () -> carreras.getCarrerasConEstudiantesInscriptos().forEach(System.out::println));
        run("g) carrera 1, ciudad Tandil", () -> carreras.getEstudiantesPorCarreraYCiudad(1, "Tandil").forEach(System.out::println));
        run("b) matricular primer estudiante en carrera 1", () -> {
            int dni = estudiantes.todosLosEstudiantesOrdenados("apellido", "ASC").get(0).getDni();
            System.out.println(inscripciones.matricular(dni, 1));
        });
        run("3) reporte por anio", () -> carreras.getReporteCarreras().forEach(System.out::println));
    }

    /** Ejecuta un paso; si falla, lo informa y sigue con el próximo. */
    private static void run(String titulo, Runnable paso) {
        System.out.println("=== " + titulo + " ===");
        try {
            paso.run();
        } catch (RuntimeException ex) {
            System.out.println("(falla: " + ex.getMessage() + ")");
        }
    }
}
