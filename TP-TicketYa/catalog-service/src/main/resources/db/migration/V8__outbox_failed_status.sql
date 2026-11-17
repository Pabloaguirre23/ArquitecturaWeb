-- El publicador marca como FAILED los eventos que agotan los reintentos (3.2).
-- El CHECK de V6 no contemplaba ese estado y hacía rollback de todo el lote.
ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS chk_outbox_status;
ALTER TABLE outbox_event ADD CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));
