# orders-service

Servicio de órdenes, pagos, entradas/QR y compensaciones de TicketYa.

- **Puerto:** 8083
- **Base:** `orders_db` (PostgreSQL 16, puerto 5435)
- **Responsable:** P3 — ver [`docs/equipo/compa-3.md`](../docs/equipo/compa-3.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 5.

## Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/orders` | autenticado | Crea la orden, cobra y emite entradas; requiere `Idempotency-Key` |
| GET | `/api/orders` | autenticado | Lista paginada de las órdenes del cliente |
| GET | `/api/orders/{ordenId}` | dueño | Detalle de la orden con sus entradas |
| GET | `/api/orders/tickets` | autenticado | Lista paginada de las entradas propias |
| GET | `/api/orders/tickets/{entradaId}` | dueño | Detalle de una entrada propia |
| POST | `/api/orders/tickets/{entradaId}/returns` | dueño | Solicita la devolución de una entrada |
| POST | `/api/orders/returns/{devolucionId}/approve` | ADMIN | Libera el lugar, invalida la entrada y reintegra |
| POST | `/api/orders/returns/{devolucionId}/reject` | ADMIN | Rechaza la devolución; la entrada sigue válida |
| POST | `/internal/events/{eventoId}/cancel` | interno | Marca las entradas vendidas `EN_REINTEGRO` y solicita los reintegros |

## Flujo de compra

1. Valida `Idempotency-Key` (8–100 caracteres) y el `token` de la pasarela.
2. Reclama la clave en `idempotency_key` con estado `EN_PROCESO` en una transacción propia.
   Si ya existe: misma respuesta (misma clave y cuerpo), `422 IDEMPOTENCY_KEY_REUSED`
   (cuerpo distinto) o `409 IDEMPOTENCY_IN_PROGRESS` con `Retry-After`.
3. Consulta la reserva a inventory (`GET /internal/reservations/{id}`), valida dueño y vigencia.
4. Guarda la orden, los ítems, el pago `PENDIENTE` y la saga en `ORDEN_CREADA` (`EN_PROCESO`)
   en una transacción corta (`OrderPersistenceService`); el total se calcula en el servidor.
5. Llama a la pasarela mock (`PaymentGateway`) con la clave `ordenId:PAGO_APROBADO`:
   `APROBADO`, `RECHAZADO` o `LENTO` (timeout).
   - `APROBADO`: registra el pago y avanza la saga a `PAGO_APROBADO`; confirma la reserva en
     inventory con su propia clave y emite las entradas en una transacción propia
     (`OrderEmissionService`). La orden pasa a `PAGADA` y la saga a `COMPLETADA` en esa misma
     transacción; una emisión vacía nunca deja la orden pagada.
   - `RECHAZADO`: orden `RECHAZADA`, sin entradas, y compensación que libera la reserva.
   - `LENTO`: `502 PAYMENT_GATEWAY_ERROR`, compensación de la reserva y ninguna orden pagada.
6. Si falla la confirmación de inventory o la emisión de entradas después del pago, la orden
   queda `REINTEGRO_PENDIENTE` y la saga en `COMPENSANDO`; la compensación se ejecuta en orden
   inverso (reintegro pendiente y luego devolución de ítems o liberación de la reserva) y deja
   el pago en `REINTEGRO_PENDIENTE` o `REINTEGRADO`, o la saga en `COMPENSADA` si todo responde.
7. Al completar, guarda la respuesta en `idempotency_key` para que los reintentos devuelvan
   exactamente la misma orden y las mismas entradas. La respuesta expone `sagaEstado` y
   `sagaPaso` para consultar el avance de la compra.

## Saga de compra

- Pasos persistidos: `RESERVA_VALIDADA` (precondición), `ORDEN_CREADA`, `PAGO_APROBADO`,
  `INVENTARIO_CONFIRMADO` y `ENTRADAS_EMITIDAS`.
- Estados: `EN_PROCESO`, `COMPLETADA`, `COMPENSANDO`, `COMPENSADA` y `FALLIDA`. Una fila de
  `saga_compra` por orden permite reanudar o compensar después de un reinicio.
- Comandos idempotentes: `ClavesIdempotencia` deriva claves estables por orden y paso
  (`ordenId:PAGO_APROBADO`, `ordenId:INVENTARIO_CONFIRMADO`, `ordenId:LIBERAR_RESERVA`,
  `pagoId:REINTEGRO:COMPRA`, `pagoId:REINTEGRO:<entradaId>`) y se envían como
  `Idempotency-Key` a inventory y a la pasarela. Reintentar un paso no duplica reserva, cobro,
  confirmación ni entradas.
- `SagaCompraRetryService` reintenta las sagas `COMPENSANDO` cada
  `orders.sagas.retry-delay-ms` (60 s por defecto) hasta `orders.sagas.max-intentos` (10); si
  se agotan, la deja `FALLIDA`. Si la compensación no se puede preparar, también queda `FALLIDA`.

## Outbox y eventos de dominio

Los eventos se escriben en `outbox_event` **en la misma transacción** que cambia el estado de
negocio: no puede existir una orden pagada, reintegradora o devolución aprobada sin su evento.
El sobre respeta el contrato de [`docs/eventos.md`](../docs/eventos.md): `eventId`, `type`,
`occurredAt`, `correlationId`, `version` y `payload`.

| Evento | Cuándo se registra |
|---|---|
| `orden.pagada` | Junto con el paso a `PAGADA` en `OrderEmissionService`. El payload usa el snapshot de la orden (incluye `eventoTipo`, `artistas` y `ciudad`). |
| `orden.reintegrada` | Al crear el reintegro durante una compensación con pago aprobado (`OrderPersistenceService.prepararCompensacion`). |
| `devolucion.aprobada` | Junto con el paso a `APROBADA` en `DevolucionService`. |

- `eventId` es determinísta (`UUID.nameUUIDFromBytes` de `type:aggregateId`), por lo que
  reintentos y compensaciones repetidas reutilizan el mismo identificador: el consumidor
  deduplica por `eventId` y no duplica efectos.
- `correlationId` se toma de la cabecera `X-Correlation-Id` (o se genera uno) y se devuelve en
  la respuesta. Para reintentos programados sin solicitud se genera uno nuevo.
- `OutboxPublisher` publica cada `fixedDelay` con `FOR UPDATE SKIP LOCKED` al exchange topic
  `ticketya.events`, usando el `type` como routing key y el sobre original como cuerpo JSON.
  Las colas y DLQ las declaran los consumidores.
- Entrega **al menos una vez**: si RabbitMQ falla, el evento queda `PENDING`, aumenta
  `attempts`, guarda `lastError` y se reprograma con backoff lineal (`base * intentos`,
  tope `max`). Agotar `maxAttempts` no borra ni marca como fallido el evento: se sigue
  reintentando con el mismo `eventId`. No hay estado `FAILED` en la migración V5.

## Devoluciones y reintentos

- Al aprobar una devolución, la entrada pasa a `EN_DEVOLUCION` (deja de ser válida) y recién
  al liberar inventory queda `DEVUELTA`. Si la pasarela o inventory fallan, la devolución
  queda `PROCESANDO` y la entrada `EN_DEVOLUCION`.
- `DevolucionRetryService` reintenta las devoluciones `PROCESANDO` cada minuto.
- `ReintegroRetryService` reintenta los `reintegro` pendientes (devoluciones, cancelación de
  evento y compensaciones) cada minuto, sin marcar nada como aprobado antes de la pasarela.
- Una devolución `PROCESANDO` no se puede rechazar: responde `409 RETURN_IN_PROGRESS`.

## Modelo

`orden`, `orden_item`, `pago`, `entrada`, `devolucion`, `idempotency_key` y `reintegro`.
La migración `V5` de la Entrega 2 agrega el snapshot de compra (`cliente_email`,
`evento_organizador_id`, `sector_nombre`, `evento_tipo`, `evento_artistas`, `evento_ciudad`),
`entrada.used_at/used_by`, las tablas `saga_compra`, `outbox_event` y `audit_log`, y los
índices de reporte por evento/sector. Flyway en
[`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5435/orders_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_URL` | `http://localhost:8082` | Cliente interno hacia inventory |
| `ORDERS_SAGAS_RETRY_DELAY_MS` | `60000` | Frecuencia del reintento de sagas `COMPENSANDO` |
| `ORDERS_SAGAS_MAX_INTENTOS` | `10` | Reintentos de compensación antes de `FALLIDA` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker del outbox |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `ticketya` | Credenciales de RabbitMQ |
| `ORDERS_OUTBOX_EXCHANGE` | `ticketya.events` | Exchange topic del productor |
| `ORDERS_OUTBOX_PUBLISH_MS` | `2000` | Frecuencia del publicador del outbox |
| `ORDERS_OUTBOX_BATCH_SIZE` | `50` | Eventos por lote del publicador |
| `ORDERS_OUTBOX_MAX_ATTEMPTS` | `10` | Intentos antes de seguir reintentando con backoff tope |
| `ORDERS_OUTBOX_BACKOFF_SECONDS` | `5` | Backoff base entre intentos |
| `ORDERS_OUTBOX_MAX_BACKOFF_SECONDS` | `300` | Tope del backoff |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d orders-db
cd orders-service
mvn test
mvn spring-boot:run
curl http://localhost:8083/actuator/health
```

`mvn test` incluye `OrderIdempotencyIntegrationTest`, `OrderTransactionIntegrationTest` y
`OrderTransactionEmissionIntegrationTest`, que usan PostgreSQL 16 con Testcontainers. Cubren
reintentos concurrentes, pasarela caída y un fallo real del repositorio dentro de la emisión
(la compensación queda persistida). Si Docker no está disponible, se omiten.

Swagger UI: <http://localhost:8083/swagger-ui.html> · OpenAPI: <http://localhost:8083/v3/api-docs>
