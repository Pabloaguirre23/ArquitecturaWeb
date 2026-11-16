-- Entrega 2 (tarea 6.1): snapshots de compra, control de acceso, saga, outbox y auditoría.
-- Migración aditiva: las columnas nuevas son nullable o tienen default, por lo que aplica
-- sobre una base de Entrega 1 sin pérdida de datos.

-- Snapshot del comprador y del evento para reportes y para el payload de `orden.pagada`
-- (ADR-017). `evento_artistas` se guarda como arreglo JSON.
ALTER TABLE orden
    ADD COLUMN cliente_email          VARCHAR(320),
    ADD COLUMN evento_organizador_id  UUID,
    ADD COLUMN sector_nombre          VARCHAR(120),
    ADD COLUMN evento_tipo            VARCHAR(40),
    ADD COLUMN evento_artistas        JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN evento_ciudad          VARCHAR(120);

-- Datos de la validación de ingreso con QR.
ALTER TABLE entrada
    ADD COLUMN used_at TIMESTAMPTZ,
    ADD COLUMN used_by UUID;

-- Índices para reportes por evento/sector y para detectar entradas utilizadas o devueltas.
CREATE INDEX idx_entrada_evento_sector_estado ON entrada (evento_id, sector_id, estado);
CREATE INDEX idx_orden_evento_estado ON orden (evento_id, estado);

-- Máquina de estados de la saga de compra (tarea 6.2).
CREATE TABLE saga_compra (
    orden_id     UUID PRIMARY KEY REFERENCES orden (id) ON DELETE CASCADE,
    paso         VARCHAR(30) NOT NULL
                 CHECK (paso IN ('RESERVA_VALIDADA', 'ORDEN_CREADA', 'PAGO_APROBADO',
                                 'INVENTARIO_CONFIRMADO', 'ENTRADAS_EMITIDAS')),
    estado       VARCHAR(20) NOT NULL DEFAULT 'EN_PROCESO'
                 CHECK (estado IN ('EN_PROCESO', 'COMPLETADA', 'COMPENSANDO', 'COMPENSADA', 'FALLIDA')),
    intentos     INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    ultimo_error TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_compra_estado ON saga_compra (estado, updated_at);

-- Outbox transaccional para `orden.pagada`, `orden.reintegrada` y `devolucion.aprobada`
-- (tarea 6.3). Mismo esquema que el outbox de catalog-service.
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    type            VARCHAR(80) NOT NULL,
    aggregate_id    UUID NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    correlation_id  VARCHAR(120),
    version         INTEGER NOT NULL DEFAULT 1,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_event_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_outbox_event_status_next ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_id, occurred_at);

-- Bitácora de operaciones críticas (tarea 9.3).
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operacion      VARCHAR(40) NOT NULL,
    entidad        VARCHAR(40) NOT NULL,
    entidad_id     UUID,
    usuario_id     UUID,
    rol            VARCHAR(20),
    resultado      VARCHAR(20) NOT NULL
                   CHECK (resultado IN ('EXITOSO', 'RECHAZADO', 'ERROR')),
    detalle        JSONB,
    correlation_id VARCHAR(120),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entidad ON audit_log (entidad, entidad_id, created_at DESC);
CREATE INDEX idx_audit_log_correlacion ON audit_log (correlation_id);
CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
