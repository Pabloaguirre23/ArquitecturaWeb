package ar.edu.unicen.ticketya.notification.config;

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
    private static final String LIST_PATH = "/api/notifications";
    private static final String DETAIL_PATH = "/api/notifications/{notificacionId}";

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - notification-service")
                        .version("1.0.0")
                        .description("""
                                Notificaciones de TicketYa persistidas en PostgreSQL y alimentadas por RabbitMQ.
                                Los endpoints públicos se consumen a través del gateway en /api/notifications/**.
                                La identidad llega como X-User-Id y X-User-Role; el cliente usa JWT contra el gateway.
                                La correlación de los eventos consumidos se persiste con cada notificación.
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
                        .addSecuritySchemes("X-Correlation-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Correlation-Id")
                                .description("Identificador de correlación del sobre de eventos; se persiste "
                                        + "con la notificación y no es obligatorio en la consulta")));
    }

    @Bean
    public OpenApiCustomizer notificationErrorResponsesCustomizer() {
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
                    marcarIdentidadObligatoria(operation);
                    enriquecerRespuestas(operation, path);
                });
            });
        };
    }

    private void registrarEsquemaDeError(Components components) {
        Schema<?> errorItem = new ObjectSchema()
                .addProperty("field", new StringSchema().example("estado"))
                .addProperty("message", new StringSchema().example("valor inválido"));
        ObjectSchema problem = new ObjectSchema();
        problem.setDescription("Error RFC 7807 con código de negocio y detalle por campo");
        problem.addProperty("type", new StringSchema()
                .example("https://ticketya.local/errors/validation-error"));
        problem.addProperty("title", new StringSchema().example("Bad Request"));
        problem.addProperty("status", new IntegerSchema().example(400));
        problem.addProperty("detail", new StringSchema().example("La solicitud tiene parámetros inválidos"));
        problem.addProperty("instance", new StringSchema().example(DETAIL_PATH));
        problem.addProperty("code", new StringSchema().example("VALIDATION_ERROR"));
        problem.addProperty("errors", new ArraySchema().items(errorItem));
        components.addSchemas("ProblemDetail", problem);
    }

    private void marcarIdentidadObligatoria(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if ("header".equals(parameter.getIn())
                    && ("X-User-Id".equals(parameter.getName())
                    || "X-User-Role".equals(parameter.getName()))) {
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
        boolean detalle = DETAIL_PATH.equals(path);
        return switch (status) {
            case "400" -> new ErrorDetalle(400, "Bad Request", "VALIDATION_ERROR",
                    "La solicitud tiene parámetros inválidos",
                    List.of(campo(detalle ? "notificacionId" : "estado", "valor inválido")));
            case "401" -> new ErrorDetalle(401, "Unauthorized", "UNAUTHORIZED",
                    "Faltan los encabezados X-User-Id y X-User-Role", List.of());
            case "403" -> detalle
                    ? new ErrorDetalle(403, "Forbidden", "NOTIFICATION_FORBIDDEN",
                            "La notificación pertenece a otro usuario", List.of())
                    : new ErrorDetalle(403, "Forbidden", "FORBIDDEN",
                            "El rol ORGANIZER no puede ejecutar esta operación", List.of());
            case "404" -> new ErrorDetalle(404, "Not Found", "NOTIFICATION_NOT_FOUND",
                    "La notificación no existe", List.of());
            case "500" -> new ErrorDetalle(500, "Internal Server Error", "INTERNAL_ERROR",
                    "Ocurrió un error inesperado en notification-service", List.of());
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
