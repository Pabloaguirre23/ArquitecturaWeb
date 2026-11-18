package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import java.util.UUID;

/**
 * Claves de idempotencia de los comandos que salen de orders-service.
 *
 * <p>La clave se compone siempre con el agregado raíz (la orden o el pago) y el
 * paso, así un reintento de la saga repite exactamente la misma clave y el
 * servicio remoto puede reconocer el comando como ya aplicado.</p>
 */
public final class ClavesIdempotencia {

    private ClavesIdempotencia() {
    }

    public static String paso(UUID ordenId, PasoSaga paso) {
        return ordenId + ":" + paso.name();
    }

    public static String liberarReserva(UUID ordenId) {
        return ordenId + ":LIBERAR_RESERVA";
    }

    public static String reintegroCompra(UUID pagoId) {
        return pagoId + ":REINTEGRO:COMPRA";
    }

    public static String reintegroEntrada(UUID pagoId, UUID entradaId) {
        return pagoId + ":REINTEGRO:" + entradaId;
    }
}
