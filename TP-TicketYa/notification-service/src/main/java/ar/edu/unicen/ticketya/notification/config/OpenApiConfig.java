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
            registrarErroresComunes(components);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null || pathItem.readOperations() == null) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> enriquecerRespuestas(operation, components));
                if (DETAIL_PATH.equals(path) && pathItem.getGet() != null) {
                    reemplazarContenido(pathItem.getGet(), "403", "NotificationForbidden", components);
                    reemplazarContenido(pathItem.getGet(), "404", "NotificationNotFound", components);
                }
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
        problem.addProperty("detail", new StringSchema().example("La notificación pertenece a otro usuario"));
        problem.addProperty("instance", new StringSchema().example("/api/notifications/{notificacionId}"));
        problem.addProperty("code", new StringSchema().example("NOTIFICATION_FORBIDDEN"));
        problem.addProperty("errors", new ArraySchema().items(errorItem));
        components.addSchemas("ProblemDetail", problem);
    }

    private void registrarErroresComunes(Components components) {
        components.addResponses("BadRequest", errorResponse(400, "Bad Request",
                "Estado o paginación inválidos", "VALIDATION_ERROR",
                "La solicitud tiene parámetros inválidos",
                List.of(Map.<String, Object>of("field", "estado", "message", "valor inválido"))));
        components.addResponses("Unauthorized", errorResponse(401, "Unauthorized",
                "Falta identidad", "UNAUTHORIZED",
                "Faltan los encabezados X-User-Id y X-User-Role", List.of()));
        components.addResponses("Forbidden", errorResponse(403, "Forbidden",
                "Permiso insuficiente", "FORBIDDEN",
                "El rol del usuario no puede consultar notificaciones", List.of()));
        components.addResponses("NotificationForbidden", errorResponse(403, "Forbidden",
                "Notificación ajena", "NOTIFICATION_FORBIDDEN",
                "La notificación pertenece a otro usuario", List.of()));
        components.addResponses("NotFound", errorResponse(404, "Not Found",
                "Recurso inexistente", "NOT_FOUND",
                "El recurso solicitado no existe", List.of()));
        components.addResponses("NotificationNotFound", errorResponse(404, "Not Found",
                "Notificación inexistente", "NOTIFICATION_NOT_FOUND",
                "No existe una notificación con el identificador indicado", List.of()));
        components.addResponses("InternalServerError", errorResponse(500, "Internal Server Error",
                "Error inesperado", "INTERNAL_ERROR",
                "Ocurrió un error inesperado en notification-service", List.of()));
    }

    private ApiResponse errorResponse(int status, String title, String description, String code,
                                      String detail, List<Map<String, Object>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ticketya.local/errors/" + code.toLowerCase().replace('_', '-'));
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", "/api/notifications");
        body.put("code", code);
        body.put("errors", errors);
        Example example = new Example().summary(description).value(body);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(PROBLEM_REF))
                .addExamples(code, example);
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON, mediaType));
    }

    private void enriquecerRespuestas(io.swagger.v3.oas.models.Operation operation, Components components) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((status, response) -> {
            if (status.startsWith("4") || status.startsWith("5")) {
                reemplazarContenido(operation, status, nombreDeError(status), components);
            }
        });
    }

    private void reemplazarContenido(io.swagger.v3.oas.models.Operation operation, String status,
                                     String nombreRespuesta, Components components) {
        ApiResponse respuesta = operation.getResponses().get(status);
        ApiResponse componente = components.getResponses().get(nombreRespuesta);
        if (respuesta != null && componente != null) {
            respuesta.setContent(componente.getContent());
        }
    }

    private String nombreDeError(String status) {
        return switch (status) {
            case "400" -> "BadRequest";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "404" -> "NotFound";
            case "500" -> "InternalServerError";
            default -> "InternalServerError";
        };
    }
}
