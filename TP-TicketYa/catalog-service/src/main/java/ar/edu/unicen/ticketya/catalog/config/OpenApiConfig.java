package ar.edu.unicen.ticketya.catalog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";
    private static final String VENUE_DETAIL_PATH = "/api/catalog/venues/{venueId}";
    private static final String CANCEL_PATH = "/api/catalog/events/{eventoId}/cancel";

    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - catalog-service")
                        .version("1.0.0")
                        .description("""
                                Catálogo de lugares, sectores, asientos, eventos, precios, publicación, edición,
                                alta demanda, búsqueda cacheada y endpoints internos de reporte y recomendación.
                                Los endpoints públicos se consumen a través del gateway en /api/catalog/**; los de
                                /internal/** requieren X-Internal-Token y no se exponen por el gateway.
                                La identidad se propaga con X-User-Id, X-User-Role y X-Correlation-Id.
                                Las respuestas cacheadas informan X-Cache: HIT, MISS o BYPASS; la disponibilidad
                                y los asientos nunca se cachean.
                                Todos los errores usan RFC 7807 (application/problem+json) con los campos code y errors[].
                                """)
                        .license(new License().name("Uso académico - UNICEN")))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("UUID del usuario autenticado en el gateway"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Rol del usuario: ADMIN u ORGANIZER según el endpoint protegido"))
                        .addSecuritySchemes("X-Internal-Token", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Token")
                                .description("Token compartido entre servicios; obligatorio en /internal/**"))
                        .addSecuritySchemes("X-Correlation-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Correlation-Id")
                                .description("Identificador de correlación propagado por el gateway")));
    }

    @Bean
    public OpenApiCustomizer catalogErrorResponsesCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() != null
                    ? openApi.getComponents()
                    : new Components();
            openApi.setComponents(components);
            registrarEsquemaDeError(components);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null || pathItem.readOperations() == null) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    marcarHeadersObligatorios(operation);
                    enriquecerRespuestas(operation, path);
                });
            });
        };
    }

    private void registrarEsquemaDeError(Components components) {
        Schema<?> errorItem = new ObjectSchema()
                .addProperty("field", new StringSchema().example("nombre"))
                .addProperty("message", new StringSchema().example("must not be blank"));
        ObjectSchema problem = new ObjectSchema();
        problem.setDescription("Error RFC 7807 con código de negocio y detalle por campo");
        problem.addProperty("type", new StringSchema()
                .example("https://ticketya.local/errors/validation-error"));
        problem.addProperty("title", new StringSchema().example("Bad Request"));
        problem.addProperty("status", new IntegerSchema().example(400));
        problem.addProperty("detail", new StringSchema().example("Uno o más campos son inválidos"));
        problem.addProperty("instance", new StringSchema().example("/api/catalog/events"));
        problem.addProperty("code", new StringSchema().example("VALIDATION_ERROR"));
        problem.addProperty("errors", new ArraySchema().items(errorItem));
        components.addSchemas("ProblemDetail", problem);
    }

    private void marcarHeadersObligatorios(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if ("header".equals(parameter.getIn())
                    && (List.of("X-User-Id", "X-User-Role", "X-Internal-Token")
                            .contains(parameter.getName()))) {
                parameter.setRequired(true);
            }
        }
    }

    private void enriquecerRespuestas(io.swagger.v3.oas.models.Operation operation, String path) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((status, response) -> {
            ErrorDetalle error = detalleError(path, status);
            if (error != null && response != null) {
                response.setContent(contenidoError(error, path));
            }
        });
    }

    private ErrorDetalle detalleError(String path, String status) {
        return switch (status) {
            case "400" -> new ErrorDetalle(400, "Bad Request", "VALIDATION_ERROR",
                    "Uno o más campos son inválidos", List.of(campo("nombre", "must not be blank")));
            case "401" -> new ErrorDetalle(401, "Unauthorized", "UNAUTHORIZED",
                    "Faltan los encabezados X-User-Id y X-User-Role", List.of());
            case "403" -> new ErrorDetalle(403, "Forbidden", "FORBIDDEN",
                    "El rol del usuario no permite esta operación", List.of());
            case "404" -> VENUE_DETAIL_PATH.equals(path)
                    ? new ErrorDetalle(404, "Not Found", "VENUE_NOT_FOUND",
                            "No existe el lugar", List.of())
                    : new ErrorDetalle(404, "Not Found", "EVENT_NOT_FOUND",
                            "No existe un evento con el identificador indicado", List.of());
            case "409" -> CANCEL_PATH.equals(path)
                    ? new ErrorDetalle(409, "Conflict", "EVENT_NOT_CANCELLABLE",
                            "No se puede cancelar un evento cancelado", List.of())
                    : new ErrorDetalle(409, "Conflict", "EVENT_NOT_EDITABLE",
                            "El evento ya no se encuentra en un estado editable", List.of());
            case "422" -> new ErrorDetalle(422, "Unprocessable Entity", "CLIENT_LIMIT_EXCEEDED",
                    "El cliente superó el límite de entradas configurado", List.of());
            case "502" -> new ErrorDetalle(502, "Bad Gateway", "INVENTORY_UNAVAILABLE",
                    "inventory no confirmó la operación", List.of());
            case "503" -> new ErrorDetalle(503, "Service Unavailable", "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la disponibilidad en inventory", List.of());
            case "500" -> new ErrorDetalle(500, "Internal Server Error", "INTERNAL_ERROR",
                    "Ocurrió un error no controlado en catalog-service", List.of());
            default -> null;
        };
    }

    private Content contenidoError(ErrorDetalle error, String instance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ticketya.local/errors/" + error.code().toLowerCase().replace('_', '-'));
        body.put("title", error.title());
        body.put("status", error.status());
        body.put("detail", error.detail());
        body.put("instance", instance);
        body.put("code", error.code());
        body.put("errors", error.errors());
        Example example = new Example().summary(error.detail()).value(body);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(PROBLEM_REF))
                .addExamples(error.code(), example);
        return new Content().addMediaType(PROBLEM_JSON, mediaType);
    }

    private Map<String, Object> campo(String field, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("message", message);
        return item;
    }

    private record ErrorDetalle(int status, String title, String code, String detail,
                                List<Map<String, Object>> errors) {
    }
}
