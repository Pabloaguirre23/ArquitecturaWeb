package tp3.controller;

import tp3.service.CarreraService;
import tp3.dto.ReporteCarreraResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final CarreraService carreraService;

    public ReporteController(CarreraService carreraService) {
        this.carreraService = carreraService;
    }

    // (h) reporte de carreras: inscriptos y egresados por anio
    @GetMapping("/carreras")
    public List<ReporteCarreraResponse> reporteCarreras() {
        return carreraService.reporteCarreras();
    }
}