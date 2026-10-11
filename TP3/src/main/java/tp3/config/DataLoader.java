package tp3.config;

import tp3.modelo.Carrera;
import tp3.modelo.Estudiante;
import tp3.modelo.Inscripcion;
import tp3.modelo.Genero;
import tp3.repository.CarreraRepository;
import tp3.repository.EstudianteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile({"default", "h2"})
public class DataLoader implements CommandLineRunner {

    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;

    public DataLoader(EstudianteRepository estudianteRepository,
                      CarreraRepository carreraRepository) {
        this.estudianteRepository = estudianteRepository;
        this.carreraRepository = carreraRepository;
    }

    @Override
    public void run(String... args) {
        Carrera computacion = carreraRepository.save(new Carrera("TU en Computacion"));
        Carrera licenciatura = carreraRepository.save(new Carrera("Licenciatura en Sistemas"));
        Carrera profesorado = carreraRepository.save(new Carrera("Profesorado en Informatica"));
        Carrera arquitectura = carreraRepository.save(new Carrera("Arquitectura de Software"));

        Estudiante juan = estudianteRepository.save(new Estudiante(
                "Juan", "Perez", 27, Genero.MASCULINO, "35300111",
                "Tandil", 1001L));
        Estudiante maria = estudianteRepository.save(new Estudiante(
                "Maria", "Lopez", 26, Genero.FEMENINO, "36111222",
                "Tandil", 1002L));
        Estudiante carlos = estudianteRepository.save(new Estudiante(
                "Carlos", "Garcia", 24, Genero.MASCULINO, "37888999",
                "Azul", 1003L));
        Estudiante ana = estudianteRepository.save(new Estudiante(
                "Ana", "Fernandez", 23, Genero.FEMENINO, "40000111",
                "Olavarria", 1004L));
        Estudiante pedro = estudianteRepository.save(new Estudiante(
                "Pedro", "Gomez", 24, Genero.MASCULINO, "39222333",
                "Tandil", 1005L));
        Estudiante lucia = estudianteRepository.save(new Estudiante(
                "Lucia", "Rodriguez", 25, Genero.FEMENINO, "41000222",
                "Necochea", 1006L));
        Estudiante diego = estudianteRepository.save(new Estudiante(
                "Diego", "Martinez", 22, Genero.MASCULINO, "41555666",
                "Tandil", 1007L));
        Estudiante sofia = estudianteRepository.save(new Estudiante(
                "Sofia", "Diaz", 21, Genero.FEMENINO, "42999888",
                "Azul", 1008L));

        Inscripcion juanTu = new Inscripcion(juan, computacion, LocalDate.of(2018, 3, 5));
        juanTu.marcarEgresado(LocalDate.of(2021, 12, 15));
        juan.matricular(juanTu);

        Inscripcion juanLic = new Inscripcion(juan, licenciatura, LocalDate.of(2019, 3, 10));
        juanLic.marcarEgresado(LocalDate.of(2021, 12, 15));
        juan.matricular(juanLic);

        Inscripcion mariaTu = new Inscripcion(maria, computacion, LocalDate.of(2019, 3, 6));
        mariaTu.marcarEgresado(LocalDate.of(2022, 11, 20));
        maria.matricular(mariaTu);

        Inscripcion mariaProf = new Inscripcion(maria, profesorado, LocalDate.of(2019, 3, 6));
        maria.matricular(mariaProf);

        Inscripcion carlosTu = new Inscripcion(carlos, computacion, LocalDate.of(2020, 3, 5));
        carlosTu.marcarEgresado(LocalDate.of(2024, 12, 18));
        carlos.matricular(carlosTu);

        Inscripcion anaLic = new Inscripcion(ana, licenciatura, LocalDate.of(2021, 3, 8));
        ana.matricular(anaLic);

        Inscripcion pedroTu = new Inscripcion(pedro, computacion, LocalDate.of(2021, 3, 3));
        pedroTu.marcarEgresado(LocalDate.of(2024, 7, 10));
        pedro.matricular(pedroTu);

        Inscripcion luciaArq = new Inscripcion(lucia, arquitectura, LocalDate.of(2020, 3, 9));
        lucia.matricular(luciaArq);

        Inscripcion luciaTu = new Inscripcion(lucia, computacion, LocalDate.of(2022, 3, 7));
        lucia.matricular(luciaTu);

        Inscripcion diegoTu = new Inscripcion(diego, computacion, LocalDate.of(2023, 3, 6));
        diego.matricular(diegoTu);

        Inscripcion sofiaProf = new Inscripcion(sofia, profesorado, LocalDate.of(2024, 3, 4));
        sofia.matricular(sofiaProf);

        Inscripcion sofiaTu = new Inscripcion(sofia, computacion, LocalDate.of(2024, 3, 4));
        sofia.matricular(sofiaTu);

        estudianteRepository.saveAll(java.util.List.of(juan, maria, carlos, ana, pedro, lucia, diego, sofia));
    }
}