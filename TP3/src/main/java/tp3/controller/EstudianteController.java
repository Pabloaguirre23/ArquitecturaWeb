package tp3.controller;

import tp3.modelo.Genero;
import tp3.service.EstudianteService;
import tp3.dto.CrearEstudianteRequest;
import tp3.dto.EstudianteResponse;
import tp3.dto.MatricularRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {

    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    // (a) dar de alta un estudiante
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstudianteResponse alta(@Valid @RequestBody CrearEstudianteRequest request) {
        return estudianteService.crear(request);
    }

    // (b) matricular un estudiante en una carrera
    @PostMapping("/{estudianteId}/matricular/{carreraId}")
    public EstudianteResponse matricular(@PathVariable Long estudianteId,
                                         @PathVariable Long carreraId,
                                         @RequestBody(required = false) @Valid MatricularRequest request) {
        return estudianteService.matricular(
                estudianteId, carreraId, request != null ? request.fechaInscripcion() : null);
    }

    // (c) recuperar todos los estudiantes, con criterio de ordenamiento simple (?orden=apellido|libreta|id)
    @GetMapping
    public List<EstudianteResponse> todos(@RequestParam(required = false) String orden) {
        return estudianteService.listar(orden);
    }

    // (d) recuperar un estudiante por numero de libreta universitaria
    @GetMapping("/libreta/{libreta}")
    public EstudianteResponse porLibreta(@PathVariable Long libreta) {
        return estudianteService.porLibretaUniversitaria(libreta);
    }

    // (e) recuperar todos los estudiantes por genero
    @GetMapping("/genero/{genero}")
    public List<EstudianteResponse> porGenero(@PathVariable Genero genero) {
        return estudianteService.porGenero(genero);
    }
}