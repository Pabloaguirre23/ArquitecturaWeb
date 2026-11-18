-- El cliente puede cancelar su reserva vigente. CANCELADA es distinto de LIBERADA,
-- que usa el pago cuando la pasarela rechaza.

ALTER TABLE reserva DROP CONSTRAINT reserva_estado_check;

ALTER TABLE reserva ADD CONSTRAINT reserva_estado_check
    CHECK (estado IN ('VIGENTE', 'CONFIRMADA', 'LIBERADA', 'VENCIDA', 'CANCELADA'));
