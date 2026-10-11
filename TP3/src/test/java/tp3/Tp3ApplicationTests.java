package tp3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Tp3ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextoCargaYListaEstudiantes() throws Exception {
        mockMvc.perform(get("/api/estudiantes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].apellido").exists());
    }

    @Test
    void recuperarEstudiantePorLibreta() throws Exception {
        mockMvc.perform(get("/api/estudiantes/libreta/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libretaUniversitaria").value(1001))
                .andExpect(jsonPath("$.nombres").value("Juan"));
    }

    @Test
    void estudiantesPorGenero() throws Exception {
        mockMvc.perform(get("/api/estudiantes/genero/FEMENINO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    void altaYMatriculacionDeEstudiante() throws Exception {
        String nuevo = """
                {
                  "nombres": "Valentina",
                  "apellido": "Rojas",
                  "edad": 20,
                  "genero": "FEMENINO",
                  "documento": "44000111",
                  "ciudadResidencia": "Tandil",
                  "libretaUniversitaria": 2001
                }
                """;

        String response = mockMvc.perform(post("/api/estudiantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.inscripciones").isEmpty())
                .andReturn().getResponse().getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/estudiantes/" + id + "/matricular/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscripciones[0].carrera").value("TU en Computacion"));
    }

    @Test
    void carrerasConInscriptos() throws Exception {
        mockMvc.perform(get("/api/carreras/inscriptos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cantidadInscriptos").isNumber());
    }

    @Test
    void reporteDeCarreras() throws Exception {
        mockMvc.perform(get("/api/reportes/carreras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].carrera").exists());
    }
}