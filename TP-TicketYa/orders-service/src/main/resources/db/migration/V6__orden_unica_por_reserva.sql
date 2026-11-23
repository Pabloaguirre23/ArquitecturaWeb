-- Entrega 2 (revisión): una reserva paga como máximo una orden.
-- Sin este índice, dos POST /orders concurrentes con la misma reserva y distinta
-- Idempotency-Key podían cobrar y emitir dos tandas de QR para los mismos asientos.
CREATE UNIQUE INDEX uq_orden_reserva ON orden (reserva_id);

-- Marca de cancelación de evento para cerrar la carrera entre la cancelación
-- (catalog -> orders) y la emisión de entradas de una compra en curso. La fila
-- se inserta en la misma transacción que mueve las entradas a EN_REINTEGRO.
CREATE TABLE evento_cancelado (
    evento_id     UUID PRIMARY KEY,
    cancelado_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
