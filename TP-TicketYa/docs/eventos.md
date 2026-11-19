# Eventos de dominio y RabbitMQ

Contrato del sobre de eventos y de los payloads que comparten los servicios. Es la base de
la tarea **1.5**; los productores (3.2, 6.3) y los consumidores (7.3, 8.2, 8.3) ya están
implementados y validados contra este esquema.

- **Estado:** congelado en la práctica. P1 y P3 verificaron sus productores con tests de
  contrato y P5 el consumidor de recommendation; queda la firma formal de la tarea 1.5 por
  P4. Cualquier cambio se discute acá y se actualizan los fixtures
  [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json) y
  [`ejemplos/evento-publicado.json`](ejemplos/evento-publicado.json) en el mismo PR.
- **Broker:** RabbitMQ 3.13, exchange `ticketya.events` de tipo `topic`.
- **Entrega:** al menos una vez. Los consumidores deduplican por `eventId`.

## 1. Sobre

```json
{
  "eventId": "e0000000-0000-4000-8000-000000000001",
  "type": "orden.pagada",
  "occurredAt": "2026-11-15T12:00:00Z",
  "correlationId": "5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f",
  "version": 1,
  "payload": {}
}
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `eventId` | UUID | sí | Identificador único del evento de dominio. Es la clave de idempotencia del consumidor y se reutiliza en los reintentos del outbox. |
| `type` | string | sí | Routing key y tipo del payload. |
| `occurredAt` | ISO-8601 UTC | sí | Momento en que ocurrió el hecho de negocio, no el de publicación. |
| `correlationId` | UUID/texto | sí | Permite seguir la operación entre servicios y logs. |
| `version` | integer | sí | Versión del schema del payload. Arranca en `1`. |
| `payload` | object | sí | Datos del evento; su forma depende de `type`. |

Reglas de compatibilidad:

- Solo cambios **aditivos**: agregar campos opcionales no rompe consumidores.
- Los consumidores ignoran campos desconocidos (`FAIL_ON_UNKNOWN_PROPERTIES=false`).
- Quitar o cambiar el significado de un campo requiere un `type` nuevo o `version` nueva.
- `eventId` no se reutiliza para un hecho distinto; un reintento del outbox conserva el mismo.

## 2. Topología

Exchange: `ticketya.events` (topic, durable). Dead-letter exchange: `ticketya.events.dlx`
(direct, durable). Cada cola tiene su DLQ `*.dlq` y `default-requeue-rejected=false`.

| Routing key | Cola | Consumidor | DLQ |
|---|---|---|---|
| `orden.pagada` | `notification.order` | notification-service (8.2) | `notification.order.dlq` |
| `orden.pagada` | `recommendation.history` | recommendation-service (7.3) | `recommendation.history.dlq` |
| `orden.reintegrada` | `notification.order` | pendiente (8.2): binding no declarado aún | `notification.order.dlq` |
| `devolucion.aprobada` | `notification.order` | pendiente (8.2): binding no declarado aún | `notification.order.dlq` |
| `evento.actualizado` | `notification.event` | notification-service (8.3) | `notification.event.dlq` |
| `evento.cancelado` | `notification.event` | notification-service (8.3) | `notification.event.dlq` |
| `evento.publicado` | `notification.favorite` | notification-service (8.3) | `notification.favorite.dlq` |
| `entrada.utilizada` | — | sin consumidor en esta entrega | — |

Un mismo evento puede tener más de una cola; cada consumidor mantiene su propia idempotencia.

## 3. Payloads

### 3.1 `orden.pagada`

Se publica **después** de que la saga confirmó inventory y emitió las entradas. Si la compra
se compensa, se publica `orden.reintegrada` en lugar de `orden.pagada`.

```json
{
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "eventoNombre": "Festival de Rock Tandil 2027",
  "eventoTipo": "FESTIVAL",
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "fechaEvento": "2027-03-15T00:00:00Z",
  "estado": "PAGADA"
}
```

| Campo | Obligatorio | Uso |
|---|---|---|
| `ordenId` | sí | Referencia de la orden y clave de idempotencia de negocio. |
| `clienteId` | sí | Dueño del historial y destinatario de la notificación. |
| `eventoId` | sí | Evento comprado; se excluye de recomendaciones futuras. |
| `eventoNombre` | recomendado | Texto de la notificación y motivo de recomendación. |
| `eventoTipo` | sí | Ranking por tipo favorito. |
| `artistas` | sí | Ranking por artista favorito; puede ser `[]`. |
| `ciudad` | sí | Ranking por ciudad y fallback de populares. |
| `fechaEvento` | sí | Filtro de eventos futuros y orden. |
| `estado` | sí | Debe ser `PAGADA`; otro valor no se registra en el historial. |

**Origen de los datos (decisión implementada, ADR-017):** snapshot al comprar. catalog
incluye `eventoTipo`, `artistas` y `ciudad` en el snapshot de publicación hacia inventory,
inventory los devuelve en la reserva y orders los persiste en `orden` (migración V5, 6.1).
`eventoNombre` y `fechaEvento` ya formaban parte de ese snapshot. recommendation **nunca**
vuelve a consultar catalog: el historial 7.3 se arma solo con este payload, así que sigue
completo aunque catalog esté caído. El endpoint interno de catalog (3.5) queda como apoyo
puntual de orders/reportes, no como fuente del historial.

### 3.2 `orden.reintegrada`

```json
{
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "monto": 25000.00,
  "motivo": "PAGO_RECHAZADO"
}
```

Ordena lo publica desde el outbox; el binding hacia `notification.order` está
pendiente de declarar en notification-service (8.2). recommendation-service **no** está
suscripto: el historial solo registra compras pagadas.

### 3.3 `evento.publicado` / `evento.actualizado` / `evento.cancelado`

```json
{
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "nombre": "Festival de Rock Tandil 2027",
  "tipo": "FESTIVAL",
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "fechaEvento": "2027-03-15T00:00:00Z",
  "organizadorId": "22222222-2222-4222-8222-222222222222",
  "estado": "PUBLICADO"
}
```

- `evento.publicado`: notification-service busca clientes con el artista favorito en la
  ciudad y les avisa (8.3).
- `evento.actualizado` y `evento.cancelado`: notification-service avisa a los compradores.
- recommendation-service puede actualizar su snapshot `eventos_vistos` en una tarea futura;
  no es obligatorio para 7.3/7.4.

### 3.4 `devolucion.aprobada`

```json
{
  "devolucionId": "9a1b0000-0000-4000-8000-000000000001",
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "entradaId": "7c2d0000-0000-4000-8000-000000000001",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "monto": 25000.00
}
```

Binding hacia `notification.order` pendiente de declarar en notification-service (8.2).

### 3.5 `entrada.utilizada`

```json
{
  "entradaId": "7c2d0000-0000-4000-8000-000000000001",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "validadoPor": "55555555-5555-4555-8555-555555555555",
  "utilizadaEn": "2027-03-14T23:10:00Z"
}
```

Se reserva para la bitácora/observabilidad de la tarea 9.3. No tiene consumidor en esta
entrega.

## 4. Idempotencia y reintentos

- **Productor:** escribe el evento en `outbox_event` en la misma transacción del cambio de
  negocio. El publicador toma pendientes con `FOR UPDATE SKIP LOCKED`, publica y marca
  `PUBLICADO`; si RabbitMQ falla, reintenta con backoff y conserva el mismo `eventId`.
- **Consumidor:** guarda o aplica el efecto una sola vez por `eventId` (o por la clave única
  de negocio). En recommendation la clave es `(eventId, clienteId, eventoId)`; en
  notification es `(event_id, tipo, destinatario_id)`, el índice único de la tabla
  `notificacion`.
- **Errores:** los mensajes inválidos o las excepciones no se reencolan infinitamente; van a
  la DLQ de la cola. La DLQ se revisa manualmente y se reintenta solo después de corregir la
  causa.
- **Orden:** no se garantiza orden entre eventos. Cada payload debe ser autosuficiente.
- **Correlación:** el `correlationId` del sobre se propaga como `X-Correlation-Id` por el
  gateway, los clientes REST, los logs y las bitácoras (9.2 en catalog, 9.3 en orders), de
  modo que una compra se pueda seguir de punta a punta.

## 5. Test de contrato

- Fixtures compartidos: [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json) y
  [`ejemplos/evento-publicado.json`](ejemplos/evento-publicado.json).
- Productores: `orders-service` (`OrdenPagadaContractTest`) y `catalog-service`
  (`EventoPublicadoEventContractTest`) deserializan los fixtures con el mismo sobre que
  publican desde el outbox.
- Consumidores: `notification-service` (`OrdenPagadaEventContractTest`,
  `EventoPublicadoEventContractTest`) y `recommendation-service`
  (`OrdenPagadaEventContractTest`) leen los mismos fixtures con el evento del listener. Si el
  contrato cambia, alguno de estos tests falla y obliga a actualizar fixtures, productores y
  consumidores en el mismo PR.
- `RecommendationRabbitIntegrationTest` valida el consumo real, el duplicado y el reintegro;
  la suite E2E (`TicketYaE2EIT`) comprueba de punta a punta que orders publica y notification
  y recommendation consumen `orden.pagada`.

## 6. Estado y deuda

| Ítem | Dueño | Tarea | Estado |
|---|---|---|---|
| Congelar este documento | P4 | 1.5 | Contenido estable y revisado por P1/P3/P5; queda la marca formal de P4 en el checklist. |
| Publicar `orden.pagada` desde el outbox de orders (§3.1) | P3 | 6.3 | Hecho en PR #25; `OrdenPagadaContractTest` y suite E2E verdes. |
| Publicar `evento.*` desde catalog (§3.3) | P1 | 3.2 | Hecho en PR #27; `OutboxPublisherTest` y test de contrato verdes. |
| Consumir `orden.pagada` en notification (§3.1) | P1 | 8.2 | Hecho: dedupe por `(event_id, tipo, destinatario_id)` y DLQ. |
| Consumir `evento.*` y avisar por artista favorito (§3.3) | P1 | 8.3 | Hecho; usa `GET /internal/preferences?artista=&ciudad=` (api-contract §6.3.1). |
| Validar el snapshot `eventoTipo`/`artistas`/`ciudad` en orders | P3 + P1/P2 | 6.1 / 3.5 | Hecho; verificado por `MigracionV5IntegrationTest` y la E2E del Día 7. |
| Declarar broker, colas y DLQ en el compose | P4 | 9.6 | Hecho: 13 contenedores healthy y healthchecks verdes. |
| Logging JSON y MDC con `X-Correlation-Id` en los siete servicios | P4 + dueños de servicio | 9.1 | Pendiente: Tiago define `logback-spring.xml` y cada dueño replica el MDC en su servicio. |
| `audit_log` de orders con la correlación de la compra | P3 | 9.3 | Pendiente: la tabla ya existe en V5; falta registrar compra, devolución, cancelación e ingreso QR y dejar evidencia. |
| Loki/Promtail/Grafana y búsqueda por correlación | P4 | 9.4 | Pendiente. |
| Colección Postman/Newman v2 por servicio | P3 + dueños | 10.2 | Pendiente: Tiago arma el esqueleto y cada dueño su carpeta. |

`entrada.utilizada` (§3.5) sigue sin consumidor: está reservada para la bitácora de 9.3 y no
debe agregarse una cola nueva sin actualizar esta tabla y el compose.
