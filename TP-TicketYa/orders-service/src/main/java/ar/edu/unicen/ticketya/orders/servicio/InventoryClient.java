package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

public interface InventoryClient {

    ReservationDto obtenerReserva(UUID reservaId);

    void confirmarReserva(UUID reservaId, String idempotencyKey);

    void liberarReserva(UUID reservaId, String motivo, String idempotencyKey);

    void devolverItems(ReturnDto devolucion);
}
