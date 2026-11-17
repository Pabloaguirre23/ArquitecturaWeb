package ar.edu.unicen.ticketya.orders.eventos;

import java.util.UUID;

/**
 * Correlación de la solicitud actual. La pobla {@code CorrelationFilter} desde
 * `X-Correlation-Id` y la usan los eventos del outbox y el log. Si no hay
 * solicitud (reintentos programados) genera una correlación nueva.
 *
 * Es estático como MDC para que no dependa de inyección: así los slices
 * {@code @WebMvcTest} que incluyen el filtro no necesitan otro bean.
 */
public final class CorrelationContext {

    private static final int MAX_LONGITUD = 120;
    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static String id() {
        String valor = ACTUAL.get();
        return valor == null || valor.isBlank() ? UUID.randomUUID().toString() : valor;
    }

    public static void set(String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String limitado = valor.trim();
        ACTUAL.set(limitado.length() > MAX_LONGITUD ? limitado.substring(0, MAX_LONGITUD) : limitado);
    }

    public static void clear() {
        ACTUAL.remove();
    }
}
