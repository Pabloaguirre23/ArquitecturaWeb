package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

/**
 * Datos del evento que orders necesita para el snapshot del `orden.pagada`
 * (tarea 6.3). Se consultan a catalog al crear la orden, con timeout y fallback:
 * si catalog no responde se sigue comprando y el evento queda con esos campos
 * vacíos, pero la venta no se cae.
 */
public interface CatalogClient {

    EventoSnapshot obtenerEvento(UUID eventoId);
}
