package ar.edu.unicen.ticketya.catalog.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige X-Internal-Token para todos los endpoints /internal/**: un endpoint
 * interno nuevo queda protegido por defecto, sin depender de que cada método
 * se acuerde de validar. Los controllers siguen validando como segunda capa.
 * Responde con el mismo formato RFC 7807 que el handler global.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private final InternalToken internalToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(InternalToken internalToken, ObjectMapper objectMapper) {
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            internalToken.require(request.getHeader("X-Internal-Token"));
        } catch (ApiException ex) {
            response.setStatus(ex.getStatus().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(problem(ex, request)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Map<String, Object> problem(ApiException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", URI.create("https://ticketya.local/errors/"
                + ex.getCode().toLowerCase().replace('_', '-')));
        body.put("title", ex.getStatus().getReasonPhrase());
        body.put("status", ex.getStatus().value());
        body.put("detail", ex.getMessage());
        body.put("instance", URI.create(request.getRequestURI()));
        body.put("code", ex.getCode());
        body.put("errors", ex.getErrors() == null ? List.of() : ex.getErrors());
        return body;
    }
}
