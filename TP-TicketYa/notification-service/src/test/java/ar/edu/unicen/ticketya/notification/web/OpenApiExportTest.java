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
    private static final String LIST_PATH = "/api/notifications";
    private static final String DETAIL_PATH = "/api/notifications/{notificacionId}";

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
        assertThat(documento.at(DETALLE + "/responses/400" + PROBLEM + "/examples/VALIDATION_ERROR")
                .isMissingNode()).isFalse();

        assertThat(esHeaderObligatorio(documento, LISTADO, "X-User-Id")).isTrue();
        assertThat(esHeaderObligatorio(documento, LISTADO, "X-User-Role")).isTrue();
        assertThat(esHeaderObligatorio(documento, DETALLE, "X-User-Id")).isTrue();
        assertThat(esHeaderObligatorio(documento, DETALLE, "X-User-Role")).isTrue();
        assertThat(documento.at("/components/schemas/ProblemDetail/properties/errors/items/type").asText())
                .isEqualTo("object");
        assertThat(documento.at(LISTADO + "/responses/401" + PROBLEM
                + "/examples/UNAUTHORIZED/value/detail").asText())
                .isEqualTo("Faltan los encabezados X-User-Id y X-User-Role");
        assertThat(documento.at(LISTADO + "/responses/401" + PROBLEM
                + "/examples/UNAUTHORIZED/value/instance").asText()).isEqualTo(LIST_PATH);
        assertThat(documento.at(LISTADO + "/responses/403" + PROBLEM
                + "/examples/FORBIDDEN/value/detail").asText())
                .isEqualTo("El rol ORGANIZER no puede ejecutar esta operación");
        assertThat(documento.at(DETALLE + "/responses/403" + PROBLEM
                + "/examples/NOTIFICATION_FORBIDDEN/value/detail").asText())
                .isEqualTo("La notificación pertenece a otro usuario");
        assertThat(documento.at(DETALLE + "/responses/404" + PROBLEM
                + "/examples/NOTIFICATION_NOT_FOUND/value/detail").asText())
                .isEqualTo("La notificación no existe");
        assertThat(documento.at(DETALLE + "/responses/404" + PROBLEM
                + "/examples/NOTIFICATION_NOT_FOUND/value/instance").asText()).isEqualTo(DETAIL_PATH);
        assertThat(documento.at(LISTADO + "/responses/200/content/application~1json").isMissingNode()).isFalse();
        assertThat(documento.at(DETALLE + "/responses/200/content/application~1json").isMissingNode()).isFalse();
        assertThat(esTipoNullable(documento, "proximoReintento")).isTrue();
        assertThat(esTipoNullable(documento, "enviadaAt")).isTrue();

        Path destino = Path.of("..", "openapi", "notification-openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, json, StandardCharsets.UTF_8);
    }

    private boolean esTipoNullable(JsonNode documento, String propiedad) {
        JsonNode tipo = documento.at("/components/schemas/NotificacionResponse/properties/"
                + propiedad + "/type");
        if (tipo.isTextual()) {
            return "null".equals(tipo.asText());
        }
        for (JsonNode valor : tipo) {
            if ("null".equals(valor.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean esHeaderObligatorio(JsonNode documento, String operacion, String nombre) {
        for (JsonNode parametro : documento.at(operacion + "/parameters")) {
            if (nombre.equals(parametro.path("name").asText())) {
                return "header".equals(parametro.path("in").asText())
                        && parametro.path("required").asBoolean();
            }
        }
        return false;
    }
}
