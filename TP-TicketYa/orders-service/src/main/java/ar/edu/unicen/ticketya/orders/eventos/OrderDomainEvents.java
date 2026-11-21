package ar.edu.unicen.ticketya.orders.eventos;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payloads de los eventos que publica orders-service. Los datos del evento son
 * siempre un snapshot tomado al comprar (ADR-017), nunca una consulta posterior
 * a catalog: así el historial y las notificaciones no dependen de que catalog
 * esté disponible cuando se consume el mensaje.
 */
public final class OrderDomainEvents {

    public static final String ORDEN_PAGADA = "orden.pagada";
    public static final String ORDEN_REINTEGRADA = "orden.reintegrada";
    public static final String DEVOLUCION_APROBADA = "devolucion.aprobada";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OrderDomainEvents() {
    }

    public static Map<String, Object> ordenPagada(Orden orden) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ordenId", texto(orden.getId()));
        payload.put("clienteId", texto(orden.getClienteId()));
        payload.put("clienteEmail", orden.getClienteEmail());
        payload.put("eventoId", texto(orden.getEventoId()));
        payload.put("eventoNombre", orden.getEventoNombre());
        payload.put("eventoTipo", orden.getEventoTipo());
        payload.put("artistas", artistas(orden.getEventoArtistas()));
        payload.put("ciudad", orden.getEventoCiudad());
        payload.put("fechaEvento", fecha(orden.getEventoFecha()));
        payload.put("estado", "PAGADA");
        return payload;
    }

    public static Map<String, Object> ordenReintegrada(Orden orden, BigDecimal monto, String motivo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ordenId", texto(orden.getId()));
        payload.put("clienteId", texto(orden.getClienteId()));
        payload.put("eventoId", texto(orden.getEventoId()));
        payload.put("monto", monto);
        payload.put("motivo", motivo);
        return payload;
    }

    public static Map<String, Object> devolucionAprobada(Devolucion devolucion, Entrada entrada,
                                                        BigDecimal monto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("devolucionId", texto(devolucion.getId()));
        payload.put("ordenId", texto(entrada.getOrdenId()));
        payload.put("entradaId", texto(entrada.getId()));
        payload.put("clienteId", texto(entrada.getClienteId()));
        payload.put("monto", monto);
        return payload;
    }

    /** Serializa los artistas del snapshot al formato JSONB de la columna `evento_artistas`. */
    public static String artistasJson(List<String> artistas) {
        if (artistas == null || artistas.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(artistas);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static List<String> artistas(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<ArrayList<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String fecha(OffsetDateTime fecha) {
        return fecha == null ? null : DateTimeFormatter.ISO_INSTANT.format(fecha.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
