package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

/** El evento no existe en catalog (404 interno). No debe confundirse con una caída del servicio. */
public class CatalogEventNotFoundException extends RuntimeException {

    public CatalogEventNotFoundException(UUID eventoId) {
        super("El evento " + eventoId + " no existe en catalog");
    }
}
