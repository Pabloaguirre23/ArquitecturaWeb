package ar.edu.unicen.ticketya.orders.servicio;

/**
 * inventory no respondió a tiempo, devolvió un 5xx o el circuito está abierto.
 * La saga la trata como un fallo reintentable: compensa o deja la orden para el
 * reintento programado, pero nunca la marca PAGADA sin inventario confirmado.
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message) {
        super(message);
    }

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
