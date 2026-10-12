package tp3.service;

import tp3.controller.exception.RecursoDuplicadoException;
import tp3.controller.exception.RecursoNoEncontradoException;
import tp3.modelo.Carrera;
import tp3.modelo.Estudiante;
import tp3.modelo.Inscripcion;
import tp3.modelo.Genero;
import tp3.repository.CarreraRepository;
import tp3.repository.InscripcionRepository;
import tp3.repository.EstudianteRepository;
import tp3.dto.CrearEstudianteRequest;
import tp3.dto.EstudianteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class EstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;

    public EstudianteService(EstudianteRepository estudianteRepository,
                             CarreraRepository carreraRepository,
                             InscripcionRepository inscripcionRepository) {
        this.estudianteRepository = estudianteRepository;
        this.carreraRepository = carreraRepository;
        this.inscripcionRepository = inscripcionRepository;
    }

    // (a) dar de alta un estudiante
    @Transactional
    public EstudianteResponse crear(CrearEstudianteRequest request) {
        if (estudianteRepository.countByDocumento(request.documento()) > 0) {
            throw new RecursoDuplicadoException(
                    "Ya existe un estudiante con el documento: " + request.documento());
        }
        if (estudianteRepository.countByLibretaUniversitaria(request.libretaUniversitaria()) > 0) {
            throw new RecursoDuplicadoException(
                    "Ya existe un estudiante con la libreta universitaria: " + request.libretaUniversitaria());
        }

        Estudiante estudiante = new Estudiante(
                request.nombres(),
                request.apellido(),
                request.edad(),
                request.genero(),
                request.documento(),
                request.ciudadResidencia(),
                request.libretaUniversitaria());

        return EstudianteResponse.from(estudianteRepository.save(estudiante));
    }

    // (b) matricular un estudiante en una carrera
    @Transactional
    public EstudianteResponse matricular(Long estudianteId, Long carreraId, LocalDate fechaInscripcion) {
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con id " + estudianteId));

        Carrera carrera = carreraRepository.findById(carreraId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una carrera con id " + carreraId));

        if (inscripcionRepository.findByEstudianteIdAndCarreraId(estudianteId, carreraId).isPresent()) {
            throw new RecursoDuplicadoException(
                    "El estudiante " + estudianteId + " ya esta matriculado en la carrera " + carreraId);
        }

        LocalDate fecha = fechaInscripcion != null ? fechaInscripcion : LocalDate.now();
        estudiante.matricular(new Inscripcion(estudiante, carrera, fecha));

        return EstudianteResponse.from(estudianteRepository.save(estudiante));
    }

    // (c) recuperar todos los estudiantes con un criterio de ordenamiento simple
    @Transactional(readOnly = true)
    public List<EstudianteResponse> listar(String orden) {
        List<Estudiante> estudiantes;
        String criterio = orden != null ? orden.toLowerCase(Locale.ROOT) : "apellido";
        switch (criterio) {
            case "libreta" -> estudiantes = estudianteRepository.findAllByOrderByLibretaUniversitariaAsc();
            case "id" -> estudiantes = estudianteRepository.findAll();
            default -> estudiantes = estudianteRepository.findAllByOrderByApellidoAscNombresAsc();
        }
        return estudiantes.stream().map(EstudianteResponse::from).toList();
    }

    // (d) recuperar un estudiante por su numero de libreta universitaria
    @Transactional(readOnly = true)
    public EstudianteResponse porLibretaUniversitaria(Long libreta) {
        Estudiante estudiante = estudianteRepository.findByLibretaUniversitaria(libreta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con libreta universitaria " + libreta));
        return EstudianteResponse.from(estudiante);
    }

    // (e) recuperar todos los estudiantes por genero
    @Transactional(readOnly = true)
    public List<EstudianteResponse> porGenero(Genero genero) {
        return estudianteRepository.findByGeneroOrderByApellidoAscNombresAsc(genero)
                .stream()
                .map(EstudianteResponse::from)
                .toList();
    }

    // (g) estudiantes de una carrera filtrados por ciudad de residencia
    @Transactional(readOnly = true)
    public List<EstudianteResponse> deCarreraEnCiudad(Long carreraId, String ciudad) {
        if (ciudad == null || ciudad.isBlank()) {
            throw new IllegalArgumentException("El parametro ciudad es obligatorio.");
        }
        carreraRepository.findById(carreraId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una carrera con id " + carreraId));

        return inscripcionRepository.findEstudiantesDeCarreraEnCiudad(carreraId, ciudad.trim())
                .stream()
                .map(EstudianteResponse::from)
                .toList();
    }
}