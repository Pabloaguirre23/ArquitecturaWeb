package ar.edu.unicen.ticketya.recommendation.config;

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
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
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

    @Bean
    public OpenAPI recommendationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - recommendation-service")
                        .version("1.0.0")
                        .description("""
                                Preferencias del cliente, historial de compras y recomendaciones sobre MongoDB.
                                Los endpoints públicos se consumen a través del gateway en /api/recommendations/**.
                                La identidad llega como X-User-Id y X-User-Role; el cliente usa JWT contra el gateway.
                                Los endpoints /internal/** exigen X-Internal-Token y no se exponen por el gateway.
                                Todos los errores usan RFC 7807 (application/problem+json) con code y errors[].
                                """)
                        .license(new License().name("Uso académico - UNICEN")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por POST /api/auth/login del gateway"))
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("UUID del usuario autenticado, propagado por el gateway"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Rol del usuario: CLIENT, ORGANIZER, ADMIN o ACCESS_STAFF"))
                        .addSecuritySchemes("X-Internal-Token", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Token")
                                .description("Token compartido entre servicios para /internal/**")));
    }

    @Bean
    public OpenApiCustomizer recommendationErrorResponsesCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() != null
                    ? openApi.getComponents()
                    : new Components();
            openApi.setComponents(components);
            registrarEsquemaDeError(components);
            registrarErroresComunes(components);
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(pathItem -> {
                    List<io.swagger.v3.oas.models.Operation> operations = pathItem.readOperations();
                    operations.forEach(operation -> enriquecerRespuestas(operation, components));
                });
            }
        };
    }

    private void registrarEsquemaDeError(Components components) {
        ObjectSchema problem = new ObjectSchema();
        problem.setDescription("Error RFC 7807 con código de negocio y detalle por campo");
        problem.addProperty("type", new StringSchema().example("https://ticketya.local/errors/validation-error"));
        problem.addProperty("title", new StringSchema().example("Bad Request"));
        problem.addProperty("status", new IntegerSchema().example(400));
        problem.addProperty("detail", new StringSchema().example("La solicitud tiene campos inválidos"));
        problem.addProperty("instance", new StringSchema().example("/api/recommendations/preferences"));
        problem.addProperty("code", new StringSchema().example("VALIDATION_ERROR"));
        problem.addProperty("errors", new ArraySchema().items(new StringSchema())
                .example("ciudad: la ciudad es obligatoria"));
        components.addSchemas("ProblemDetail", problem);
    }

    private void registrarErroresComunes(Components components) {
        components.addResponses("BadRequest", errorResponse(400, "Bad Request",
                "Solicitud inválida", "VALIDATION_ERROR",
                "La solicitud tiene campos inválidos",
                List.of("ciudad: la ciudad es obligatoria")));
        components.addResponses("InvalidEventType", errorResponse(400, "Bad Request",
                "Tipo de evento desconocido", "INVALID_EVENT_TYPE",
                "El tipo de evento OPERA no es conocido",
                List.of("tipos[0]: valores admitidos: [RECITAL, TEATRO, DEPORTE, CONFERENCIA, FESTIVAL, OTRO]")));
        components.addResponses("Unauthorized", errorResponse(401, "Unauthorized",
                "Falta identidad o token interno", "UNAUTHORIZED",
                "Faltan los encabezados X-User-Id y X-User-Role", List.of()));
        components.addResponses("Forbidden", errorResponse(403, "Forbidden",
                "Permiso insuficiente", "FORBIDDEN",
                "El rol del usuario no permite esta operación", List.of()));
        components.addResponses("InternalServerError", errorResponse(500, "Internal Server Error",
                "Error inesperado", "INTERNAL_ERROR",
                "Ocurrió un error no controlado en recommendation-service", List.of()));
    }

    private ApiResponse errorResponse(int status, String title, String detail, String code,
                                      String exampleDetail, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ticketya.local/errors/" + code.toLowerCase().replace('_', '-'));
        body.put("title", title);
        body.put("status", status);
        body.put("detail", exampleDetail);
        body.put("instance", "/api/recommendations/preferences");
        body.put("code", code);
        body.put("errors", errors);
        Example example = new Example().summary(detail).value(body);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(PROBLEM_REF))
                .addExamples(code, example);
        return new ApiResponse()
                .description(detail)
                .content(new Content().addMediaType(PROBLEM_JSON, mediaType));
    }

    private void enriquecerRespuestas(io.swagger.v3.oas.models.Operation operation,
                                      Components components) {
        ApiResponses responses = operation.getResponses() != null
                ? operation.getResponses()
                : new ApiResponses();
        operation.setResponses(responses);
        responses.putIfAbsent("400", components.getResponses().get("BadRequest"));
        responses.putIfAbsent("500", components.getResponses().get("InternalServerError"));
        responses.forEach((status, response) -> {
            if (status.startsWith("4") || status.startsWith("5")) {
                ApiResponse component = components.getResponses().get(nombreDeError(status));
                if (component != null) {
                    response.setContent(component.getContent());
                }
            }
        });
    }

    private String nombreDeError(String status) {
        return switch (status) {
            case "400" -> "BadRequest";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "500" -> "InternalServerError";
            default -> "InternalServerError";
        };
    }
}
