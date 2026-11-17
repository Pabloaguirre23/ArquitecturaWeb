package ar.edu.unicen.ticketya.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.notification.config.OpenApiConfig;
import ar.edu.unicen.ticketya.notification.servicio.ConsultaNotificacionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exporta el OpenAPI generado por springdoc a openapi/notification-openapi.json
 * (relativo a la raíz del repositorio, un nivel arriba de notification-service).
 */
@WebMvcTest(NotificacionController.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import(OpenApiConfig.class)
class OpenApiExportTest {

    private static final String LISTADO = "/paths/~1api~1notifications/get";
    private static final String DETALLE = "/paths/~1api~1notifications~1{notificacionId}/get";
    private static final String PROBLEM = "/content/application~1problem+json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultaNotificacionService consultaService;

    @Test
    void exportaElOpenApiDeNotification() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode documento = new ObjectMapper().readTree(json);
        assertThat(documento.at("/paths").size()).isEqualTo(2);
        assertThat(documento.at("/components/schemas/ProblemDetail").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/bearerAuth").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Id").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Role").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-Correlation-Id").isMissingNode()).isFalse();
        assertThat(documento.at(LISTADO).isMissingNode()).isFalse();
        assertThat(documento.at(DETALLE).isMissingNode()).isFalse();

        assertThat(documento.at(LISTADO + "/responses/400" + PROBLEM + "/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProblemDetail");
        assertThat(documento.at(LISTADO + "/responses/401" + PROBLEM + "/examples/UNAUTHORIZED")
                .isMissingNode()).isFalse();
        assertThat(documento.at(LISTADO + "/responses/403" + PROBLEM + "/examples/FORBIDDEN")
                .isMissingNode()).isFalse();
        assertThat(documento.at(DETALLE + "/responses/403" + PROBLEM + "/examples/NOTIFICATION_FORBIDDEN")
                .isMissingNode()).isFalse();
        assertThat(documento.at(DETALLE + "/responses/404" + PROBLEM + "/examples/NOTIFICATION_NOT_FOUND")
                .isMissingNode()).isFalse();

        Path destino = Path.of("..", "openapi", "notification-openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, json, StandardCharsets.UTF_8);
    }
}
