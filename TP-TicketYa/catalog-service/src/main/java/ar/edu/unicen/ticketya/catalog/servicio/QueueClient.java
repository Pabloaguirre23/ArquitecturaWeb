package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;

public interface QueueClient {

    void abrir(UUID eventoId);

    void cerrar(UUID eventoId, String motivo);
}
