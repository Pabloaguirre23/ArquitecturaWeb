package ar.edu.unicen.ticketya.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.config.OpenApiConfig;
import ar.edu.unicen.ticketya.catalog.servicio.AuditService;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.servicio.VenueService;
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
 * Exporta el OpenAPI generado por springdoc a openapi/catalog-openapi.json
 * (relativo a la raíz del repositorio, un nivel arriba de catalog-service).
 */
@WebMvcTest(controllers = {VenueController.class, EventController.class, InternalEventController.class,
        InternalCatalogController.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import(OpenApiConfig.class)
class OpenApiExportTest {

    private static final String PROBLEM = "/content/application~1problem+json";
    private static final String VENUE_DETAIL = "/paths/~1api~1catalog~1venues~1{venueId}/get";
    private static final String CANCEL = "/paths/~1api~1catalog~1events~1{eventoId}~1cancel/post";
    private static final String PUBLISH = "/paths/~1api~1catalog~1events~1{eventoId}~1publish/post";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;
    @MockitoBean
    private EventService eventService;
    @MockitoBean
    private InternalToken internalToken;
    @MockitoBean
    private AuditService auditService;

    @Test
    void exportaElOpenApiDeCatalog() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode documento = new ObjectMapper().readTree(json);
        assertThat(documento.at("/paths").size()).isEqualTo(11);
        assertThat(documento.at("/components/schemas/ProblemDetail").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Id").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Role").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-Internal-Token").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-Correlation-Id").isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1events~1{eventoId}~1availability/patch")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1events~1{eventoId}~1report/get")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1recommendations~1candidates/get")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1events~1{eventoId}~1report/get/security/0/X-Internal-Token")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1events~1{eventoId}~1availability/patch/security/0/X-Internal-Token")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1catalog~1events/get/responses/200/headers/X-Cache")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1catalog~1events~1{eventoId}/get/responses/200/headers/X-Cache")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1catalog~1venues/get/responses/200/headers/X-Cache")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1catalog~1venues~1{venueId}/get/responses/200/headers/X-Cache")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1catalog~1events/post/responses/400/content/"
                        + "application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProblemDetail");
        assertThat(documento.at("/paths/~1api~1catalog~1events~1{eventoId}~1publish/post/responses/502/"
                        + "content/application~1problem+json/examples/INVENTORY_UNAVAILABLE").isMissingNode())
                .isFalse();
        assertThat(documento.at("/components/schemas/ProblemDetail/properties/errors/items/type").asText())
                .isEqualTo("object");
        assertThat(documento.at("/components/schemas/ProblemDetail/properties/type/example").asText())
                .startsWith("https://ticketya.local/errors/");
        assertThat(documento.at(VENUE_DETAIL + "/responses/404" + PROBLEM
                + "/examples/VENUE_NOT_FOUND/value/code").asText()).isEqualTo("VENUE_NOT_FOUND");
        assertThat(documento.at(CANCEL + "/responses/409" + PROBLEM
                + "/examples/EVENT_NOT_CANCELLABLE/value/code").asText()).isEqualTo("EVENT_NOT_CANCELLABLE");
        assertThat(documento.at(PUBLISH + "/responses/502" + PROBLEM
                + "/examples/INVENTORY_UNAVAILABLE/value/code").asText()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(esHeaderObligatorio(documento, "/paths/~1internal~1events~1{eventoId}~1report/get",
                "X-Internal-Token")).isTrue();

        Path destino = Path.of("..", "openapi", "catalog-openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, json, StandardCharsets.UTF_8);
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
