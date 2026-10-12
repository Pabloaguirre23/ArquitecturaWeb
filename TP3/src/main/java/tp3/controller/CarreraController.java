package tp3.controller;

import tp3.service.CarreraService;
import tp3.service.EstudianteService;
import tp3.dto.CarreraConInscriptosResponse;
import tp3.dto.EstudianteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/carreras")
public class CarreraController {

    private final CarreraService carreraService;
    private final EstudianteService estudianteService;

    public CarreraController(CarreraService carreraService, EstudianteService estudianteService) {
        this.carreraService = carreraService;
        this.estudianteService = estudianteService;
    }

    // (f) carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
    @GetMapping("/inscriptos")
    public List<CarreraConInscriptosResponse> carrerasConInscriptos() {
        return carreraService.carrerasConInscriptos();
    }

    // (g) estudiantes de una determinada carrera, filtrados por ciudad de residencia
    @GetMapping("/{carreraId}/estudiantes")
    public List<EstudianteResponse> estudiantesDeCarreraEnCiudad(@PathVariable Long carreraId,
                                                                 @RequestParam String ciudad) {
        return estudianteService.deCarreraEnCiudad(carreraId, ciudad);
    }
}