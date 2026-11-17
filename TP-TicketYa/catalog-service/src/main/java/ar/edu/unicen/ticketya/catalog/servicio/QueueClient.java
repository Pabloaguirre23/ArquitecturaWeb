package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;

/**
 * Cliente interno hacia queue-service. Catálogo solo abre y cierra la fila;
 * el estado de los clientes y los tokens viven en queue-service.
 */
public interface QueueClient {

    /** Abre la fila del evento. La operación es idempotente. */
    void abrir(UUID eventoId);

    /** Cierra la fila con el motivo indicado: CANCELADO, FINALIZADO o AGOTADO. */
    void cerrar(UUID eventoId, String motivo);
}
