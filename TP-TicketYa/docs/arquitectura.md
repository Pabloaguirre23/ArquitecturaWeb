# Arquitectura — TicketYa Entrega 2

Este documento resume los límites de servicio, las dependencias, la estrategia de consistencia y los flujos críticos de la Entrega 2: compra con saga/outbox, fila virtual, acceso con QR, notificaciones y reportes.
El detalle técnico completo está en [`../openspec/changes/entrega-2-completo/design.md`](../openspec/changes/entrega-2-completo/design.md) (y en [`../openspec/changes/entrega-1-core/design.md`](../openspec/changes/entrega-1-core/design.md) el origen de la Entrega 1), el contrato en [`api-contract.md`](api-contract.md), el modelo de cada base en [`modelo-datos.md`](modelo-datos.md), las decisiones con sus alternativas en [`decisiones.md`](decisiones.md) y el contrato de eventos en [`eventos.md`](eventos.md).

## 1. Diagrama de módulos

```mermaid
flowchart LR
    subgraph Cliente
        U[Usuario / Postman / curl]
        W[web :3000]
    end

    subgraph Borde
        G[api-gateway :8080<br/>login y JWT HS256<br/>roles y X-User-*<br/>X-Correlation-Id]
    end

    subgraph Servicios
        C[catalog-service :8081<br/>eventos, búsqueda y cache<br/>alta demanda y reporte interno]
        I[inventory-service :8082<br/>disponibilidad y reservas<br/>valida token de fila]
        O[orders-service :8083<br/>órdenes, pago, QR y reportes<br/>saga, outbox y bitácora]
        Q[queue-service :8086<br/>fila virtual en Redis]
        R[recommendation-service :8084<br/>MongoDB e historial]
        N[notification-service :8085<br/>consumidores y reintentos]
    end

    subgraph Datos
        CDB[(catalog_db<br/>PostgreSQL)]
        IDB[(inventory_db<br/>PostgreSQL)]
        ODB[(orders_db<br/>PostgreSQL)]
        NDB[(notification_db<br/>PostgreSQL)]
        REDIS[(Redis<br/>fila y cache)]
        MONGO[(MongoDB<br/>recomendaciones)]
    end

    MQ[[RabbitMQ<br/>ticketya.events<br/>topic + DLQ]]
    PAGO[[Pasarela mock<br/>APROBADO / RECHAZADO / LENTO]]

    U -->|HTTPS| G
    W -->|HTTPS| G
    G -->|"/api/catalog/**"| C
    G -->|"/api/inventory/**"| I
    G -->|"/api/orders/**"| O
    G -->|"/api/queue/**"| Q
    G -->|"/api/recommendations/**"| R
    G -->|"/api/notifications/**"| N

    C --> CDB
    I --> IDB
    O --> ODB
    N --> NDB
    C -->|cache Redis| REDIS
    Q -->|fila y tokens| REDIS
    R --> MONGO

    C -->|"POST /internal/events/{eventoId}/inventory"| I
    C -->|"POST /internal/events/{eventoId}/cancel"| O
    O -->|"GET /internal/events/{eventoId}/report"| C
    O -->|"GET /internal/reservations/{id} y confirm/release/returns"| I
    N -->|"GET /internal/preferences/{clienteId}"| R

    C -->|outbox| MQ
    O -->|outbox| MQ
    MQ -->|notification.order / notification.event / notification.favorite| N
    MQ -->|recommendation.history| R
    O -->|cobrar| PAGO
```

- El cliente nunca llama a los servicios por dentro: todo entra por el gateway, que valida el JWT, aplica la matriz por rol, descarta los `X-User-*` del cliente y propaga la identidad y `X-Correlation-Id`.
- Cada servicio es dueño de su base o colección y no comparte tablas. Los cruces son REST por `/internal/**` (con `X-Internal-Token`) o eventos de dominio por RabbitMQ.
- `inventory-service` valida el token de la fila localmente con `QUEUE_TOKEN_SECRET`, sin llamar a `queue-service` en el camino crítico de la reserva.
- Los productores escriben el evento en un `outbox_event` dentro de la transacción de negocio; un publicador separado lo entrega a RabbitMQ con al menos una vez.
- El compose levanta los siete servicios, cuatro PostgreSQL, MongoDB, Redis y RabbitMQ. Solo el gateway (8080) y la web (3000) publican puertos. Los usuarios de prueba del login viven en `DemoUserStore` (BCrypt en memoria): `auth_db`, previsto en el `design.md`, queda como evolución no implementada en esta entrega.

### Límites de servicio y dueño del dato

| Servicio | Responsabilidad | Datos propios | No hace |
|---|---|---|---|
| `api-gateway` | Punto de entrada, login, JWT HS256, autorización por rol, correlación y ruteo | Usuarios de prueba en memoria con BCrypt | No contiene reglas de negocio |
| `catalog-service` | Lugares, eventos, precios, publicación, búsqueda paginada, cache, alta demanda y datos de reporte | `catalog_db` + claves `catalog:*` en Redis | No escribe disponibilidad ni emite entradas |
| `inventory-service` | Disponibilidad, reservas, no doble venta, límite por cliente y validación del token de fila | `inventory_db` | No conoce el catálogo completo ni los pagos |
| `orders-service` | Órdenes, pago mock, entradas/QR, acceso, reportes, devoluciones, saga, outbox y bitácora | `orders_db` | No modifica catálogo ni disponibilidad por su cuenta |
| `queue-service` | Fila virtual, posiciones, tandas y tokens temporales de compra | Redis (`queue:{eventoId}:*`) | No decide disponibilidad ni reserva lugares |
| `recommendation-service` | Preferencias, historial y recomendaciones | MongoDB `recommendation_db` | No consulta catalog para armar el historial |
| `notification-service` | Consumo de eventos, envío simulado, reintentos y DLQ | `notification_db` | No bloquea la compra ni la cancelación |

Los cruces usan IDs y snapshots: catalog publica hacia inventory, inventory confirma o libera ante orders y orders emite las entradas. Ninguna base tiene claves foráneas contra otra; el detalle está en [`modelo-datos.md`](modelo-datos.md).

## 2. Secuencia de compra con saga y outbox

Cubre los tres resultados de la pasarela, la reserva vencida y los pasos persistidos de la saga (tareas 4.3 a 4.7 y 6.2/6.3). El evento `orden.pagada`, la bitácora `COMPRA_APROBADA` y el cambio de estado comparten la misma `X-Correlation-Id`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as Gateway
    participant I as inventory-service
    participant O as orders-service
    participant P as Pasarela mock
    participant S as saga_compra
    participant B as outbox_event
    participant MQ as RabbitMQ
    participant DB as orders_db

    C->>G: POST /api/orders + Idempotency-Key + Bearer JWT
    G->>O: POST /api/orders + X-User-Id / X-User-Role / X-Correlation-Id
    O->>DB: INSERT idempotency_key EN_PROCESO
    O->>I: GET /internal/reservations/{id} (valida token de fila si aplica)
    I-->>O: Reserva VIGENTE
    alt Reserva vencida
        O-->>G: 410 RESERVATION_EXPIRED
    else Reserva vigente
        O->>S: RESERVA_VALIDADA → ORDEN_CREADA
        O->>DB: INSERT orden PENDIENTE_PAGO + items + pago
        O->>P: cobrar(monto, token, clave ordenId:PAGO)
        alt Pago APROBADO
            P-->>O: APROBADO
            O->>S: PAGO_APROBADO
            O->>I: POST /internal/reservations/{id}/confirm (idempotente)
            alt Confirmación y emisión OK
                I-->>O: CONFIRMADA, asientos VENDIDO
                O->>S: INVENTARIO_CONFIRMADO
                O->>DB: TX: orden PAGADA + entradas con QR + outbox orden.pagada
                O->>S: ENTRADAS_EMITIDAS
                O->>DB: TX aparte: audit_log COMPRA_APROBADA + idempotency_key COMPLETADA
                O-->>G: 201 Orden PAGADA + entradas
                G-->>C: 201 + X-Correlation-Id
                O->>B: OutboxPublisher toma pendientes con FOR UPDATE SKIP LOCKED
                B->>MQ: orden.pagada (eventId, correlationId, snapshot del evento)
                MQ->>MQ: notification.order + recommendation.history
            else Falla la confirmación o la emisión
                O->>S: COMPENSANDO
                O->>DB: orden REINTEGRO_PENDIENTE + pago REINTEGRADO + outbox orden.reintegrada
                O->>I: liberar o devolver lugares
                O->>DB: COMPENSADA (o reintentable) + idempotency_key COMPLETADA
                O-->>G: 201 Orden REINTEGRO_PENDIENTE sin entradas
            end
        else Pago RECHAZADO
            P-->>O: RECHAZADO
            O->>DB: orden RECHAZADA + pago RECHAZADO
            O->>I: POST /internal/reservations/{id}/release
            O->>DB: idempotency_key COMPLETADA
            O-->>G: 201 Orden RECHAZADA sin entradas
        else Pasarela LENTO / timeout
            P--x O: timeout
            O->>DB: orden ERROR_PAGO + pago RECHAZADO
            O->>I: POST /internal/reservations/{id}/release
            O->>DB: DELETE idempotency_key EN_PROCESO
            O-->>G: 502 PAYMENT_GATEWAY_ERROR
        end
    end
```

- Cada comando a inventory y a la pasarela usa una clave de idempotencia derivada de `ordenId:paso`; repetir un paso no duplica reserva, cobro, confirmación ni entrada.
- La emisión ocurre en una transacción propia junto con el cambio a `PAGADA` y el `outbox_event`. Nunca existe una orden `PAGADA` sin entradas válidas.
- El `audit_log` se escribe con `REQUIRES_NEW` y captura su propio error: si la bitácora falla, la compra no se revierte.
- El publicador reintenta con backoff sin perder el `eventId`; los consumidores deduplican por `eventId`.

## 3. Fila virtual

Para eventos de alta demanda, `queue-service` ordena el acceso en Redis y `inventory-service` exige un token temporal firmado al reservar. Es polling con tandas, no WebSockets.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as Gateway
    participant Q as queue-service :8086
    participant R as Redis
    participant I as inventory-service

    C->>G: POST /api/queue/events/{eventId}/join
    G->>Q: join con la identidad del JWT
    Q->>R: ZADD NX queue:{eventoId}:waiting (score = llegada + secuencia)
    Q-->>C: posición, EN_ESPERA y estimación
    loop polling cada 2-5 s
        C->>Q: GET /api/queue/events/{eventId}/position
        Q-->>C: posición y estado
    end
    Q->>R: scheduler mueve hasta batchSize clientes a admitted con Lua atómica
    Q->>Q: emite JWT de fila (jti, exp 5 min, type=QUEUE)
    Q-->>C: HABILITADO + token temporal
    C->>G: POST /api/inventory/reservations + X-Queue-Token
    G->>I: reserva con token
    I->>I: valida firma con QUEUE_TOKEN_SECRET, sin llamar a queue-service
    alt Token vigente
        I-->>C: 201 Reserva VIGENTE
    else Ausente, vencido o de otro cliente/evento
        I-->>C: 403 QUEUE_REQUIRED / QUEUE_TOKEN_INVALID
    end
```

- Reingresar conserva la posición (`ZADD NX`); las tandas respetan el orden de llegada y no habilitan a quien está detrás.
- Al cancelar o agotar el evento la fila se cierra; los tokens ya emitidos vencen por TTL. Redis con AOF conserva posiciones ante reinicio; si Redis falla, los eventos de alta demanda hacen *fail closed*.

## 4. Acceso con QR

El dueño del QR es `orders-service`. El uso único se garantiza con un `UPDATE` condicional atómico, igual que la no doble venta, nunca con `find` + `save`.

```mermaid
sequenceDiagram
    autonumber
    actor A as Personal de acceso
    participant G as Gateway
    participant O as orders-service
    participant DB as orders_db
    participant L as audit_log

    A->>G: POST /api/orders/access/validate + Bearer (ACCESS_STAFF o ADMIN)
    G->>O: qrCode, eventoId, X-User-Id / X-User-Role / X-Correlation-Id
    O->>DB: UPDATE condicional de entrada a UTILIZADA (used_at, used_by)
    alt 1 fila afectada
        O->>L: INGRESO_VALIDADO con personal y correlación
        O-->>A: 200 entrada UTILIZADA
    else 0 filas
        O->>DB: SELECT entrada por qrCode para distinguir el motivo
        O->>L: INGRESO_RECHAZADO con motivo y correlación
        O-->>A: 404 TICKET_NOT_FOUND o 409 TICKET_ALREADY_USED / TICKET_WRONG_EVENT / TICKET_NOT_VALID
    end
```

La condición del `UPDATE` es `estado = 'VALIDA' AND used_at IS NULL` sobre el `qrCode` y el `eventoId`. Dos escaneos concurrentes del mismo QR producen exactamente un `200` y un `409`. Cada intento se registra en `audit_log` con `X-Correlation-Id`; la escritura es best-effort y no revierte el ingreso. No se guarda la imagen del QR.

## 5. Publicación de un evento

```mermaid
sequenceDiagram
    autonumber
    actor A as Organizador
    participant G as Gateway
    participant C as catalog-service
    participant I as inventory-service
    participant Q as queue-service
    participant MQ as RabbitMQ

    A->>G: POST /api/catalog/events/{id}/publish
    G->>C: publish con identidad del organizador
    C->>C: Valida BORRADOR y precios completos
    C->>I: POST /internal/events/{id}/inventory (snapshot con tipo, artistas y ciudad)
    I->>I: Upsert idempotente por eventoId + version
    alt Inventory responde
        I-->>C: 200
        C->>C: Estado PUBLICADO, evicta cache y escribe outbox evento.publicado
        C->>Q: abre la fila si el evento es de alta demanda
        C->>MQ: evento.publicado (notification.favorite)
        C-->>G: 200 Evento PUBLICADO
    else Inventory falla
        C-->>G: 502 INVENTORY_UNAVAILABLE
        Note over C: El evento sigue en BORRADOR
    end
```

## 6. Cancelación de evento y devolución

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin o cliente
    participant G as Gateway
    participant C as catalog-service
    participant O as orders-service
    participant I as inventory-service

    A->>G: POST /api/catalog/events/{id}/cancel o POST /api/orders/tickets/{id}/returns
    G->>C: cancel (ADMIN)
    C->>C: Estado CANCELADO y outbox evento.cancelado
    C->>O: POST /internal/events/{id}/cancel
    O->>O: Entradas VALIDA → EN_REINTEGRO, saga y audit_log CANCELACION_EVENTO
    O->>O: Reintegros PENDIENTE → pasarela → APROBADO
    O->>I: devolución de lugares con referencia idempotente
    O->>O: outbox orden.reintegrada / devolucion.aprobada
    alt Orders responde
        O-->>C: 200 {entradasAfectadas, reintegrosSolicitados}
        C->>C: cancelacionPendiente = false
    else Orders no responde
        C->>C: cancelacionPendiente = true
        Note over C: El scheduler reintenta cada 60 s
    end
    C-->>G: 200 {estado, cancelacionPendiente}
```

La devolución aprobada reutiliza `POST /internal/returns` de inventory con una `referencia` para que la reposición de zonas no numeradas sea idempotente. `DevolucionRetryService` y `ReintegroRetryService` reintentan los estados `PROCESANDO` y `PENDIENTE`.

## 7. Notificaciones asíncronas

La compra nunca espera a notification-service. Los eventos salen del outbox y cada consumidor aplica su efecto una sola vez por `eventId`.

```mermaid
sequenceDiagram
    autonumber
    participant B as outbox_event
    participant MQ as RabbitMQ ticketya.events
    participant N as notification-service
    participant DB as notification_db
    participant MQDLQ as DLQ
    participant R as recommendation-service

    B->>MQ: publicación con eventId, correlationId y versión
    par Confirmación de compra
        MQ->>N: orden.pagada → notification.order
        N->>N: dedupe por eventId
        N->>DB: notificacion PENDIENTE → ENVIADA (sender mock)
    and Historial de compras
        MQ->>R: orden.pagada → recommendation.history
        R->>R: dedupe por eventId, upsert en historial_compras
    and Cambios de evento
        MQ->>N: evento.publicado / evento.actualizado / evento.cancelado
        N->>N: artista favorito consulta /internal/preferences/{clienteId}
    end
    alt Envío fallido
        N->>DB: PENDIENTE, intentos++ y next_retry_at con backoff
        Note over N: tras 5 intentos queda FALLIDA y se deriva a la DLQ
        N->>MQDLQ: mensaje venenoso
    end
```

- La topología, las routing keys y los payloads están congelados en [`eventos.md`](eventos.md). `notification.order`, `notification.event`, `notification.favorite` y `recommendation.history` tienen su DLQ `*.dlq`.
- `orden.reintegrada` y `devolucion.aprobada` ya se publican desde el outbox, pero sus bindings hacia `notification.order` siguen pendientes de declarar; `entrada.utilizada` no tiene consumidor en esta entrega.
- Si RabbitMQ está caído, el evento permanece en `outbox_event` y el publicador reintenta; la operación de negocio ya se completó.

## 8. Reporte del organizador

```mermaid
sequenceDiagram
    autonumber
    actor O as Organizador o ADMIN
    participant G as Gateway
    participant R as orders-service
    participant C as catalog-service
    participant DB as orders_db

    O->>G: GET /api/orders/reports/events/{eventoId}
    G->>R: X-User-Id / X-User-Role
    R->>C: GET /internal/events/{eventoId}/report con X-Internal-Token
    alt Catalog responde
        C-->>R: organizadorId, sectores y capacidad
        R->>DB: ventas, devueltas y reintegros por sector
        R-->>O: 200 con ocupación, recaudación y totales
    else Catalog caído
        R->>R: usa caché de 60 s o el snapshot de orden
        R-->>O: 200 con datosDesactualizados=true (503 si no hay dueño verificable)
    end
```

El reporte calcula vendidas, devueltas, netas, ocupación (`netas / capacidad`) y recaudación por sector. La capacidad y el organizador viven en catalog; los importes y las entradas viven en orders. Un organizador ajeno recibe `403 NOT_EVENT_OWNER`.

## 9. Decisiones de consistencia

| Situación | Mecanismo |
|---|---|
| Doble venta de asientos | `SELECT ... FOR UPDATE` ordenado por `asientoId` dentro de la transacción |
| Sobreventa de zonas | `UPDATE ... WHERE disponibles >= N RETURNING` |
| Acceso a eventos de alta demanda | Token de fila firmado validado por inventory; *fail closed* si Redis no responde |
| Reintento de compra | `idempotency_key` con claim `EN_PROCESO` y respuesta guardada |
| Pasos de la compra | Saga orquestada en `saga_compra` con claves `ordenId:paso` y compensaciones |
| Evento perdido entre servicios | `outbox_event` en la misma transacción + publicación con al menos una entrega |
| Evento duplicado | `eventId` registrado por cada consumidor (idempotencia) |
| Pago aprobado con fallo posterior | Orden `REINTEGRO_PENDIENTE`, pago `REINTEGRADO` y liberación/devolución |
| Cancelación sin orders | `cancelacion_pendiente` + scheduler de reintento |
| Uso único del QR | `UPDATE` condicional atómico sobre `estado` y `used_at` |
| Notificación lenta o caída | Consumo asíncrono, reintentos con backoff y DLQ; la compra no espera |
| Bitácora caída | `audit_log` con `REQUIRES_NEW` y error capturado: no revierte la operación |
| Reporte con catalog caído | Caché de 60 s o snapshot local y `datosDesactualizados: true` |
| Reserva vencida | Scheduler cada 30 s y validación perezosa al consultar/pagar |

La consistencia es **fuerte dentro de cada servicio** (transacción local + constraints) y **eventual entre servicios**. La disponibilidad se lee de inventory, el QR y las ventas de orders, las preferencias de MongoDB y la capacidad de catalog. Los snapshots congelan nombre, fecha, precio, tipo, artistas y ciudad para que la compra, las notificaciones y las recomendaciones no dependan de una llamada en vivo. Las alternativas y consecuencias están en [`decisiones.md`](decisiones.md) (ADR-005 a ADR-019) y en el [`design.md`](../openspec/changes/entrega-2-completo/design.md) (decisiones 1 a 15).

## 10. Deuda y trabajo pendiente

Resueltas respecto de la Entrega 1: la saga y el outbox formales (6.2/6.3), la búsqueda paginada con cache Redis, el contrato de eventos con snapshot de `orden.pagada` (ADR-017/018) y la identidad por JWT en el gateway.

Sigue pendiente o con simplificación documentada:

- **Observabilidad centralizada:** logs JSON con correlación y MDC ya están en los servicios; Loki, Promtail y Grafana quedan para la tarea 9.4.
- **`auth_db`:** el login usa usuarios de prueba con BCrypt en memoria (`DemoUserStore`); la base de usuarios del `design.md` no está implementada.
- **Bindings de eventos:** `orden.reintegrada` y `devolucion.aprobada` no están enlazados a `notification.order`, y `entrada.utilizada` no tiene consumidor; ver [`eventos.md`](eventos.md).
- **N+1 en el listado de órdenes:** `OrderQueryService` sigue consultando ítems y entradas por orden; no se abordó en esta entrega.
- **Reservas vencidas e idempotencia huérfana:** el scheduler libera cada 30 s y una clave `EN_PROCESO` se limpia al registrar una clave nueva; el comportamiento se conserva de la Entrega 1.
- **Verificación con el stack levantado:** los tests Testcontainers y la suite E2E (8 flujos) requieren Docker; se ejecutan con `docker compose up --build` y `TICKETYA_E2E=true mvn -B -ntp verify` desde `integration-tests`.
