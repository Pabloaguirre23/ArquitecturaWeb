package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.eventos.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma `X-Correlation-Id` (o genera uno) y lo deja disponible para los eventos
 * del outbox y para los logs de la solicitud con el mismo valor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String CLAVE_MDC = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlacion = request.getHeader(CABECERA);
        if (correlacion == null || correlacion.isBlank()) {
            correlacion = UUID.randomUUID().toString();
        }
        CorrelationContext.set(correlacion);
        MDC.put(CLAVE_MDC, CorrelationContext.id());
        response.setHeader(CABECERA, CorrelationContext.id());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CLAVE_MDC);
            CorrelationContext.clear();
        }
    }
}
