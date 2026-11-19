package ar.edu.unicen.ticketya.gateway.web;

import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Punto único de identidad de la Entrega 2.
 *
 * <p>Valida el JWT (firma y expiración), aplica la matriz de roles por método y ruta, descarta
 * cualquier encabezado de identidad que mande el cliente y propaga X-User-Id, X-User-Role,
 * X-User-Email y X-Correlation-Id tomados del token. Los servicios siguen leyendo los mismos
 * encabezados. Lo que no figura en la matriz se deniega (default-deny).</p>
 */
@Component
public class IdentityHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> ROLES = Set.of("CLIENT", "ORGANIZER", "ADMIN", "ACCESS_STAFF");
    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> ORGANIZER_ONLY = Set.of("ORGANIZER");
    private static final Set<String> CLIENT_ONLY = Set.of("CLIENT");
    private static final Set<String> ACCESS_STAFF_OR_ADMIN = Set.of("ACCESS_STAFF", "ADMIN");
    private static final Set<String> AUTHENTICATED = Set.of("CLIENT", "ORGANIZER", "ADMIN");

    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/api/catalog/events",
            "/api/catalog/venues");

    private static final Pattern PUBLIC_GET_PATTERN = Pattern.compile(
            "^/api/catalog/(events|venues)/[0-9a-fA-F-]{36}$"
                    + "|^/api/catalog/venues/[0-9a-fA-F-]{36}/sectors$"
                    + "|^/api/inventory/events/[0-9a-fA-F-]{36}/availability$"
                    + "|^/api/inventory/events/[0-9a-fA-F-]{36}/seats$");

    private static final String ID = "[0-9a-fA-F-]{36}";

    private record Rule(HttpMethod method, Pattern path, Set<String> roles) {}

    private record Identity(String userId, String role, String email) {}

    private static Rule rule(HttpMethod method, String regex, Set<String> roles) {
        return new Rule(method, Pattern.compile("^" + regex + "$"), roles);
    }

    /** Lista cerrada de rutas protegidas del contrato. Lo que no está acá se deniega. */
    private static final List<Rule> RULES = List.of(
            // catalog
            rule(HttpMethod.POST, "/api/catalog/venues", ADMIN_ONLY),
            rule(HttpMethod.POST, "/api/catalog/venues/" + ID + "/sectors", ADMIN_ONLY),
            rule(HttpMethod.POST, "/api/catalog/events", ORGANIZER_ONLY),
            rule(HttpMethod.PUT, "/api/catalog/events/" + ID, ORGANIZER_ONLY),
            rule(HttpMethod.PUT, "/api/catalog/events/" + ID + "/prices/" + ID, ORGANIZER_ONLY),
            rule(HttpMethod.POST, "/api/catalog/events/" + ID + "/publish", ORGANIZER_ONLY),
            rule(HttpMethod.POST, "/api/catalog/events/" + ID + "/cancel", ADMIN_ONLY),
            // inventory
            rule(HttpMethod.POST, "/api/inventory/reservations", CLIENT_ONLY),
            rule(HttpMethod.GET, "/api/inventory/reservations/" + ID, CLIENT_ONLY),
            rule(HttpMethod.DELETE, "/api/inventory/reservations/" + ID, CLIENT_ONLY),
            // orders
            rule(HttpMethod.POST, "/api/orders", AUTHENTICATED),
            rule(HttpMethod.GET, "/api/orders", AUTHENTICATED),
            rule(HttpMethod.GET, "/api/orders/" + ID, AUTHENTICATED),
            rule(HttpMethod.GET, "/api/orders/tickets", AUTHENTICATED),
            rule(HttpMethod.GET, "/api/orders/tickets/" + ID, AUTHENTICATED),
            rule(HttpMethod.POST, "/api/orders/tickets/" + ID + "/returns", AUTHENTICATED),
            rule(HttpMethod.POST, "/api/orders/returns/" + ID + "/approve", ADMIN_ONLY),
            rule(HttpMethod.POST, "/api/orders/returns/" + ID + "/reject", ADMIN_ONLY),
            rule(HttpMethod.POST, "/api/orders/access/validate", ACCESS_STAFF_OR_ADMIN),
            // queue
            rule(HttpMethod.POST, "/api/queue/events/" + ID + "/join", CLIENT_ONLY),
            rule(HttpMethod.GET, "/api/queue/events/" + ID + "/position", CLIENT_ONLY),
            // recommendation
            rule(HttpMethod.GET, "/api/recommendations", CLIENT_ONLY),
            rule(HttpMethod.GET, "/api/recommendations/preferences", CLIENT_ONLY),
            rule(HttpMethod.PUT, "/api/recommendations/preferences", CLIENT_ONLY),
            rule(HttpMethod.GET, "/api/recommendations/events/" + ID + "/similar", CLIENT_ONLY),
            // notification
            rule(HttpMethod.GET, "/api/notifications", CLIENT_ONLY),
            rule(HttpMethod.GET, "/api/notifications/" + ID, CLIENT_ONLY));

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    public IdentityHeaderFilter(ObjectMapper objectMapper, JwtService jwtService) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        final String correlationId = resolveCorrelationId(request.getHeaders().getFirst(CORRELATION_HEADER));
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);

        // Nada de lo que el cliente declare sobre su identidad se propaga a los servicios.
        ServerHttpRequest.Builder requestBuilder = request.mutate().headers(headers -> {
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_ROLE_HEADER);
            headers.remove(USER_EMAIL_HEADER);
            headers.remove(INTERNAL_TOKEN_HEADER);
            headers.set(CORRELATION_HEADER, correlationId);
        });

        boolean publicRead = HttpMethod.GET.equals(method)
                && (PUBLIC_GET_PATHS.contains(path) || PUBLIC_GET_PATTERN.matcher(path).matches());
        if (publicRead) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || authorization.substring(BEARER_PREFIX.length()).isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Falta el encabezado Authorization: Bearer <token>");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        final Identity identity;
        try {
            identity = readIdentity(jwtService.parse(token));
        } catch (ExpiredJwtException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "El token expiró");
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "El token es inválido");
        }

        if (!requiredRoles(path, method).contains(identity.role())) {
            return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "El rol " + identity.role() + " no está autorizado para esta operación");
        }

        requestBuilder.headers(headers -> {
            headers.set(USER_ID_HEADER, identity.userId());
            headers.set(USER_ROLE_HEADER, identity.role());
            headers.set(USER_EMAIL_HEADER, identity.email());
        });
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private static Identity readIdentity(Claims claims) {
        String subject = claims.getSubject();
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        if (subject == null || role == null || !ROLES.contains(role) || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Identidad incompleta");
        }
        return new Identity(UUID.fromString(subject).toString(), role, email);
    }

    private Set<String> requiredRoles(String path, HttpMethod method) {
        for (Rule rule : RULES) {
            if (rule.method().equals(method) && rule.path().matcher(path).matches()) {
                return rule.roles();
            }
        }
        return Set.of(); // default-deny
    }

    private static String resolveCorrelationId(String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            try {
                return UUID.fromString(incoming.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // no es un UUID: se genera uno nuevo
            }
        }
        return UUID.randomUUID().toString();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://ticketya.local/errors/" + code.toLowerCase()));
        problem.setInstance(URI.create(exchange.getRequest().getURI().getPath()));
        problem.setProperty("code", code);
        problem.setProperty("errors", List.of());

        try {
            byte[] body = objectMapper.writeValueAsBytes(problem);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            byte[] fallback = ("{\"status\":" + status.value() + ",\"code\":\"" + code + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}