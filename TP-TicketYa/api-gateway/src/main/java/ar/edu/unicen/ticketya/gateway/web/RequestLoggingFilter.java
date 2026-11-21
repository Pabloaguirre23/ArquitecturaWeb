package ar.edu.unicen.ticketya.gateway.web;

import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Deja una línea de log JSON por request con correlationId, usuario, rol, método, ruta,
 * estado y duración. Corre antes que IdentityHeaderFilter para registrar también los 401/403.
 * En WebFlux el MDC no viaja entre hilos: se completa justo antes de escribir el log.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String[] CLAVES = {"correlationId", "userId", "role", "method", "path", "status", "durationMs"};

    private final JwtService jwtService;

    public RequestLoggingFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long inicio = System.nanoTime();
        String metodo = exchange.getRequest().getMethod().name();
        String ruta = exchange.getRequest().getURI().getPath();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return chain.filter(exchange)
                .doFinally(senal -> registrar(exchange, metodo, ruta, authorization, inicio));
    }

    private void registrar(ServerWebExchange exchange, String metodo, String ruta,
                           String authorization, long inicio) {
        String userId = null;
        String role = null;
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            try {
                Claims claims = jwtService.parse(authorization.substring(BEARER_PREFIX.length()).trim());
                userId = claims.getSubject();
                role = claims.get("role", String.class);
            } catch (RuntimeException ex) {
                // token inválido o vencido: se loguea sin identidad
            }
        }
        HttpStatusCode estado = exchange.getResponse().getStatusCode();
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
        poner("correlationId", exchange.getResponse().getHeaders().getFirst(CORRELATION_HEADER));
        poner("userId", userId);
        poner("role", role);
        poner("method", metodo);
        poner("path", ruta);
        poner("status", estado == null ? null : String.valueOf(estado.value()));
        poner("durationMs", String.valueOf(duracionMs));
        try {
            log.info("request completada");
        } finally {
            for (String clave : CLAVES) {
                MDC.remove(clave);
            }
        }
    }

    private static void poner(String clave, String valor) {
        if (valor != null) {
            MDC.put(clave, valor);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
