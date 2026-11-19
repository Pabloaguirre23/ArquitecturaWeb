package ar.edu.unicen.ticketya.gateway.auth;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/** Errores del login en el mismo formato RFC 7807 del contrato. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> credencialesInvalidas(ServerWebExchange exchange) {
        return problem(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Credenciales inválidas", List.of());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> camposInvalidos(WebExchangeBindException ex, ServerWebExchange exchange) {
        List<Map<String, String>> errors = ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return problem(exchange, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud tiene campos inválidos", errors);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> cuerpoInvalido(ServerWebExchange exchange) {
        return problem(exchange, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El cuerpo de la solicitud es inválido", List.of());
    }

    private ResponseEntity<ProblemDetail> problem(ServerWebExchange exchange, HttpStatus status,
                                                  String code, String detail, List<?> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://ticketya.local/errors/" + code.toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(exchange.getRequest().getURI().getPath()));
        problem.setProperty("code", code);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}