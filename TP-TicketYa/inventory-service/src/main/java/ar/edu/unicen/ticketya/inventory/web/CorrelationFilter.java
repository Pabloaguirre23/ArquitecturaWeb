package ar.edu.unicen.ticketya.inventory.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma `X-Correlation-Id` (o genera uno), lo deja en el MDC y en la respuesta, y escribe
 * una línea de log por request con usuario, rol, método, ruta, estado y duración (9.1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String CLAVE_MDC = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);
    private static final String[] CLAVES_LOG = {"userId", "role", "method", "path", "status", "durationMs"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlacion = request.getHeader(CABECERA);
        if (correlacion == null || correlacion.isBlank()) {
            correlacion = UUID.randomUUID().toString();
        }
        long inicio = System.nanoTime();
        MDC.put(CLAVE_MDC, correlacion);
        response.setHeader(CABECERA, correlacion);
        try {
            chain.doFilter(request, response);
        } finally {
            registrar(request, response, inicio);
            MDC.remove(CLAVE_MDC);
        }
    }

    private void registrar(HttpServletRequest request, HttpServletResponse response, long inicio) {
        String ruta = request.getRequestURI();
        if (ruta.startsWith("/actuator")) {
            return;
        }
        poner("userId", request.getHeader("X-User-Id"));
        poner("role", request.getHeader("X-User-Role"));
        poner("method", request.getMethod());
        poner("path", ruta);
        poner("status", String.valueOf(response.getStatus()));
        poner("durationMs", String.valueOf((System.nanoTime() - inicio) / 1_000_000));
        try {
            log.info("request completada");
        } finally {
            for (String clave : CLAVES_LOG) {
                MDC.remove(clave);
            }
        }
    }

    private static void poner(String clave, String valor) {
        if (valor != null && !valor.isBlank()) {
            MDC.put(clave, valor);
        }
    }
}
