-- Alta demanda y el jti del token de fila que autorizó la reserva.
-- El token se valida en inventory. No se consulta queue-service.

ALTER TABLE evento_inventario
    ADD COLUMN alta_demanda BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE reserva
    ADD COLUMN queue_jti UUID,
    ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'DIRECTA'
        CHECK (origen IN ('DIRECTA', 'FILA'));
