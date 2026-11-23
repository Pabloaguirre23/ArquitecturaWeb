# Contrato REST — TicketYa (Entrega 2 — congelado 20/11/2026)

Este documento es la **vista humana** del contrato. La fuente machine-readable es el OpenAPI de cada servicio (`/v3/api-docs` y Swagger UI en `/swagger-ui.html`). Los dos se actualizan en el mismo PR.

- **Estado:** Entrega 2, congelado el 20/11/2026. Todo cambio posterior se hace por PR con aviso a los dueños de cada servicio.
- **Entrada única:** `http://localhost:8080` (api-gateway). Los servicios no se consumen directo desde el cliente.
- **Prefijos:** `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`, `/api/recommendations/**` y, en la Entrega 2, `/api/auth/**`, `/api/queue/**`, `/api/notifications/**` y `/api/orders/access|reports/**`.
- **Internos:** `/internal/**`, solo entre servicios, nunca expuestos por el gateway. Exigen `X-Internal-Token`.

## 1. Convenciones

### 1.1 Identidad (Entrega 1)

| Encabezado | Formato | Cuándo |
|---|---|---|
| `X-User-Id` | UUID | Obligatorio en endpoints protegidos; lo exige el gateway |
| `X-User-Role` | `CLIENT`, `ORGANIZER`, `ADMIN` o `ACCESS_STAFF` | Obligatorio en endpoints protegidos |
| `X-Correlation-Id` | UUID o texto corto | Opcional; el gateway lo genera y propaga |
| `Idempotency-Key` | 8 a 100 caracteres | Obligatorio en `POST /api/orders` |
| `X-Internal-Token` | Token compartido | Obligatorio en `/internal/**`; el gateway lo borra del tráfico externo |

- Header ausente o `X-User-Id` que no sea UUID → **401**.
- Rol no habilitado para la ruta → **403**.
- Las lecturas públicas (búsqueda, detalle, disponibilidad, mapa de asientos) no requieren headers.
- En la Entrega 2 el gateway valida el JWT (`Authorization: Bearer <token>`) y propaga `X-User-Id`, `X-User-Role` y `X-User-Email` tomados del token. Los `X-User-*` que mande el cliente se descartan, así que ya no autentican. Los servicios no cambian.
- Token vencido o inválido → **401**. Ruta que no figura en las reglas del gateway → **403**.

### 1.2 Formato de error (RFC 7807)

`Content-Type: application/problem+json`

```json
{
  "type": "https://ticketya.local/errors/seat-not-available",
  "title": "Conflict",
  "status": 409,
  "detail": "El asiento 7f2c... ya no está disponible",
  "instance": "/api/inventory/reservations",
  "code": "SEAT_NOT_AVAILABLE",
  "errors": []
}
```

En errores de validación, `errors[]` trae `field` y `message`:

```json
{
  "type": "https://ticketya.local/errors/validation-error",
  "title": "Bad Request",
  "status": 400,
  "detail": "La solicitud tiene campos inválidos",
  "instance": "/api/catalog/events",
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "fecha", "message": "debe ser una fecha futura" }
  ]
}
```

### 1.3 Paginación

Todas las listas paginadas usan la misma forma:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Parámetros: `page` (desde 0, default 0), `size` (default 20, máximo 100). Un `size` mayor a 100 se recorta a 100.

### 1.4 Fechas, montos e IDs

- IDs: UUID en texto.
- Fechas: ISO-8601 con offset `America/Argentina/Buenos_Aires`; se aceptan y devuelven con offset, por ejemplo `2027-01-15T21:00:00-03:00`.
- Montos: número JSON con dos decimales, en ARS (`15000.00`). Se calculan siempre en el servidor.
- Enums: mayúsculas con guion bajo, tal como se listan en este documento.

### 1.5 Enums congelados

| Enum | Valores |
|---|---|
| `TipoEvento` | `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL`, `OTRO` |
| `EstadoEvento` | `BORRADOR`, `PUBLICADO`, `AGOTADO`, `CANCELADO`, `FINALIZADO` |
| `TipoSector` | `NUMERADO`, `NO_NUMERADO` |
| `EstadoReserva` | `VIGENTE`, `CONFIRMADA`, `LIBERADA`, `VENCIDA` |
| `EstadoOrden` | `PENDIENTE_PAGO`, `PAGADA`, `RECHAZADA`, `ERROR_PAGO`, `REINTEGRO_PENDIENTE` |
| `EstadoEntrada` | `VALIDA`, `UTILIZADA`, `DEVUELTA`, `EN_REINTEGRO`, `EN_DEVOLUCION` |
| `EstadoDevolucion` | `PENDIENTE`, `PROCESANDO`, `APROBADA`, `RECHAZADA` |
| `EstadoPago` | `PENDIENTE`, `APROBADO`, `RECHAZADO`, `REINTEGRADO`, `REINTEGRO_PENDIENTE`, `PARCIALMENTE_REINTEGRADO` |
| `MedioPago` | `TARJETA`, `EFECTIVO`, `TRANSFERENCIA` |
| Token de la pasarela mock | `APROBADO`, `RECHAZADO`, `LENTO` |

### 1.6 Login — `POST /api/auth/login` (público)

Lo atiende el gateway. Usuarios demo en memoria, contraseña `demo`:
`cliente@ticketya.test` (CLIENT), `organizador@ticketya.test` (ORGANIZER),
`admin@ticketya.test` (ADMIN), `acceso@ticketya.test` (ACCESS_STAFF).

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ticketya.test","password":"demo"}'
```

```json
{
  "token": "<jwt>",
  "role": "CLIENT",
  "email": "cliente@ticketya.test",
  "nombre": "Cliente Demo",
  "expiresAt": "2026-11-19T02:37:47Z"
}
```

| Caso | Código | `code` |
|---|---|---|
| Credenciales inválidas | `401` | `UNAUTHORIZED` |
| Falta `email` o `password` | `400` | `VALIDATION_ERROR` |

El token se manda en cada pedido protegido: `Authorization: Bearer <token>`. Dura 8 h por defecto (`JWT_TTL`).

## 2. Resumen de endpoints

| Método | Ruta | Rol | Éxito | Servicio |
|---|---|---|---|---|
| POST | `/api/auth/login` | público | 200 | api-gateway |
| POST | `/api/catalog/venues` | ADMIN | 201 | catalog |
| GET | `/api/catalog/venues` | público | 200 | catalog |
| GET | `/api/catalog/venues/{venueId}` | público | 200 | catalog |
| POST | `/api/catalog/venues/{venueId}/sectors` | ADMIN | 201 | catalog |
| GET | `/api/catalog/venues/{venueId}/sectors` | público | 200 | catalog |
| POST | `/api/catalog/events` | ORGANIZER | 201 | catalog |
| PUT | `/api/catalog/events/{eventoId}` | ORGANIZER | 200 | catalog |
| PUT | `/api/catalog/events/{eventoId}/prices/{sectorId}` | ORGANIZER | 200 | catalog |
| POST | `/api/catalog/events/{eventoId}/publish` | ORGANIZER | 200 | catalog |
| POST | `/api/catalog/events/{eventoId}/cancel` | ADMIN | 200 | catalog |
| GET | `/api/catalog/events` | público | 200 | catalog |
| GET | `/api/catalog/events/{eventoId}` | público | 200 | catalog |
| GET | `/api/inventory/events/{eventoId}/availability` | público | 200 | inventory |
| GET | `/api/inventory/events/{eventoId}/seats` | público | 200 | inventory |
| POST | `/api/inventory/reservations` | CLIENT | 201 | inventory |
| GET | `/api/inventory/reservations/{reservaId}` | CLIENT dueño | 200 | inventory |
| DELETE | `/api/inventory/reservations/{reservaId}` | CLIENT dueño | 204 | inventory |
| POST | `/api/orders` | autenticado | 201 | orders |
| GET | `/api/orders` | autenticado | 200 | orders |
| GET | `/api/orders/{ordenId}` | dueño | 200 | orders |
| GET | `/api/orders/tickets` | autenticado | 200 | orders |
| GET | `/api/orders/tickets/{entradaId}` | dueño | 200 | orders |
| POST | `/api/orders/tickets/{entradaId}/returns` | dueño | 201 | orders |
| POST | `/api/orders/returns/{devolucionId}/approve` | ADMIN | 200 | orders |
| POST | `/api/orders/returns/{devolucionId}/reject` | ADMIN | 200 | orders |
| POST | `/api/orders/access/validate` | ACCESS_STAFF o ADMIN | 200 | orders |
| GET | `/api/orders/reports/events/{eventoId}` | ORGANIZER dueño o ADMIN | 200 | orders |
| GET | `/api/recommendations/preferences` | CLIENT | 200 | recommendation |
| PUT | `/api/recommendations/preferences` | CLIENT | 200 | recommendation |
| GET | `/api/recommendations` | CLIENT | 200 | recommendation |
| GET | `/api/notifications` | CLIENT | 200 | notification |
| GET | `/api/notifications/{notificacionId}` | CLIENT (dueño) | 200 | notification |
| GET | `/api/recommendations/events/{eventId}/similar` | CLIENT | 200 | recommendation |
| POST | `/api/queue/events/{eventoId}/join` | CLIENT | 201/200 | queue |
| GET | `/api/queue/events/{eventoId}/position` | CLIENT | 200 | queue |

“Autenticado” significa `CLIENT`, `ORGANIZER` o `ADMIN`; el dueño del recurso se valida dentro del servicio.
Los endpoints `/internal/**` no se listan porque no se exponen por el gateway.

## 3. catalog-service

OpenAPI: [`../openapi/catalog-openapi.json`](../openapi/catalog-openapi.json). Swagger UI en `http://localhost:8081/swagger-ui.html`. El documento incluye los esquemas `X-User-Id`, `X-User-Role`, `X-Correlation-Id` y `X-Internal-Token`, la cabecera `X-Cache` en las consultas cacheadas y ejemplos de `ProblemDetail`. La publicación de eventos de dominio se describe en [3.15](#315-eventos-de-dominio-y-outbox-tarea-32).

### 3.1 Crear lugar — `POST /api/catalog/venues` (ADMIN)

```json
{
  "nombre": "Estadio Municipal",
  "direccion": "Av. Siempre Viva 742",
  "ciudad": "Tandil",
  "latitud": -37.3217,
  "longitud": -59.1332
}
```

**201 Created** + `Location: /api/catalog/venues/{id}`

```json
{
  "id": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "nombre": "Estadio Municipal",
  "direccion": "Av. Siempre Viva 742",
  "ciudad": "Tandil",
  "latitud": -37.3217,
  "longitud": -59.1332
}
```

Errores: `400 VALIDATION_ERROR` (latitud/longitud fuera de rango, campos vacíos), `403 FORBIDDEN`.

### 3.2 Listar lugares — `GET /api/catalog/venues?page=0&size=20` (público)

**200** con paginación de `VenueResponse`.

### 3.3 Detalle de lugar — `GET /api/catalog/venues/{venueId}` (público)

**200** `VenueResponse`; `404 VENUE_NOT_FOUND`.

### 3.4 Crear sector — `POST /api/catalog/venues/{venueId}/sectors` (ADMIN)

Sector numerado:

```json
{ "nombre": "Platea A", "tipo": "NUMERADO", "filas": 10, "asientosPorFila": 20 }
```

Sector no numerado:

```json
{ "nombre": "Campo", "tipo": "NO_NUMERADO", "capacidad": 5000 }
```

**201 Created**

```json
{
  "id": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "nombre": "Platea A",
  "tipo": "NUMERADO",
  "capacidad": 200,
  "filas": 10,
  "asientosPorFila": 20,
  "asientosGenerados": 200
}
```

Errores: `400 VALIDATION_ERROR` (capacidad ≤ 0, sector numerado sin filas, capacidad > 20000, filas > 100 o asientosPorFila > 200), `403`, `404 VENUE_NOT_FOUND`, `409 SECTOR_ALREADY_EXISTS` (ya existe un sector con ese nombre en el lugar), `409 VENUE_HAS_PUBLISHED_EVENTS` (el lugar tiene eventos `PUBLICADO`/`AGOTADO`; un sector nuevo quedaría sin precio y rompería la edición del evento).

### 3.5 Listar sectores — `GET /api/catalog/venues/{venueId}/sectors` (público)

**200** con la lista de `SectorResponse`; `404 VENUE_NOT_FOUND`.

### 3.6 Crear evento — `POST /api/catalog/events` (ORGANIZER)

```json
{
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "fecha": "2027-01-15T21:00:00-03:00",
  "participantes": ["Artista A", "Artista B"],
  "limitePorCliente": 4,
  "altaDemanda": true
}
```

**201 Created**

```json
{
  "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "organizadorId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "fecha": "2027-01-15T21:00:00-03:00",
  "estado": "BORRADOR",
  "limitePorCliente": 4,
  "altaDemanda": true,
  "participantes": ["Artista A", "Artista B"],
  "precios": []
}
```

Errores: `400 VALIDATION_ERROR` (fecha pasada, tipo inválido, límite ≤ 0, participantes vacío), `403`, `404 VENUE_NOT_FOUND`.

`altaDemanda: true` marca el evento para fila virtual: al publicarse, catalog abre la fila en queue-service (ver 3.14). `limitePorCliente` se configura aquí y viaja en el snapshot y en el detalle; la reducción del límite una vez registradas ventas queda pendiente de contrato con orders/inventory (tarea 3.1).

### 3.6.1 Editar evento — `PUT /api/catalog/events/{eventoId}` (ORGANIZER)

Edición parcial: solo se actualizan los campos enviados. Se puede usar en `BORRADOR`, `PUBLICADO` y `AGOTADO`; un evento `CANCELADO` o `FINALIZADO` responde `409`.

```json
{
  "nombre": "Festival de Verano 2027",
  "descripcion": "Nueva descripción",
  "tipo": "FESTIVAL",
  "fecha": "2027-06-01T21:00:00-03:00",
  "participantes": ["Artista A", "Artista C"],
  "limitePorCliente": 6
}
```

**200** con `EventResponse`. Si el evento está `PUBLICADO` o `AGOTADO`, catalog envía el snapshot completo a inventory con `version` incrementada y el nuevo `altaDemanda`, publica `evento.actualizado` mediante el outbox, invalida el detalle y las búsquedas cacheadas y vuelve a abrir la fila si es de alta demanda (idempotente).

Reglas:

- `altaDemanda` solo se puede cambiar mientras el evento está en `BORRADOR`; después responde `409`.
- `fecha` debe ser futura.
- `limitePorCliente` debe ser mayor a cero.
- `participantes`, si viene, reemplaza la lista completa y no puede quedar vacío.
- El lugar solo se puede cambiar en `BORRADOR`. En eventos publicados/agotados responde `409` hasta que inventory soporte reemplazar sus sectores y asientos.

Errores: `400 VALIDATION_ERROR` (fecha pasada, límite ≤ 0, participantes vacío, nombre vacío), `401 UNAUTHORIZED`, `403 NOT_EVENT_OWNER`, `404 EVENT_NOT_FOUND`/`VENUE_NOT_FOUND`, `409 EVENT_NOT_EDITABLE` (evento terminal, cambio de alta demanda o de lugar publicado), `409 EVENT_INCOMPLETE`, `502 INVENTORY_UNAVAILABLE`.

### 3.7 Asignar precio — `PUT /api/catalog/events/{eventoId}/prices/{sectorId}` (ORGANIZER)

```json
{ "precio": 15000.00 }
```

**200**

```json
{ "eventoId": "1f2e...", "sectorId": "9a1b...", "precio": 15000.00 }
```

Errores: `400 SECTOR_NOT_FROM_VENUE`, `400 INVALID_PRICE` (precio negativo), `403 NOT_EVENT_OWNER`, `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`, `409 EVENT_NOT_EDITABLE` (evento ya publicado).

### 3.8 Publicar evento — `POST /api/catalog/events/{eventoId}/publish` (ORGANIZER)

Sin cuerpo. **200** `EventResponse` con `estado: "PUBLICADO"`. Si `altaDemanda` es `true`, catalog registra `evento.publicado` en el outbox y abre la fila en queue-service (ver 3.14).

Errores: `403 NOT_EVENT_OWNER`, `404 EVENT_NOT_FOUND`, `409 EVENT_NOT_EDITABLE` (no está en `BORRADOR`), `409 EVENT_INCOMPLETE` (algún sector sin precio), `502 INVENTORY_UNAVAILABLE` (inventory no confirmó; el evento queda `BORRADOR`). Un fallo de queue no modifica la respuesta: se registra un warning y la apertura se reintenta en una edición posterior.

### 3.9 Cancelar evento — `POST /api/catalog/events/{eventoId}/cancel` (ADMIN)

Sin cuerpo. **200**

```json
{
  "eventoId": "1f2e...",
  "estado": "CANCELADO",
  "cancelacionPendiente": false
}
```

Catalog cierra primero la venta en inventory con `PATCH /internal/events/{eventoId}/state` y recién después avisa a orders. Si inventory falla, no se avisa a orders y `cancelacionPendiente` queda en `true`; el scheduler reintenta ambos avisos en orden. Si orders falla después de cerrar la venta, también queda pendiente. Para eventos de alta demanda también se cierra la fila con motivo `CANCELADO` (ver 3.14); un fallo de queue no revierte la cancelación.

Errores: `404 EVENT_NOT_FOUND`, `409 EVENT_NOT_CANCELLABLE` (ya cancelado o finalizado). Un fallo de inventory/orders no cambia el 200: se refleja en `cancelacionPendiente`.

### 3.10 Buscar eventos — `GET /api/catalog/events` (público)

Query params (todos opcionales y combinables):

| Parámetro | Tipo | Descripción |
|---|---|---|
| `tipo` | `TipoEvento` | Tipo exacto |
| `organizadorId` | UUID | Organizador exacto |
| `participante` | texto | Coincidencia parcial, sin distinguir mayúsculas |
| `ciudad` | texto | Ciudad del lugar, coincidencia exacta sin distinguir mayúsculas |
| `fechaDesde` | ISO-8601 | Fecha/hora mínima del evento |
| `fechaHasta` | ISO-8601 | Fecha/hora máxima del evento |
| `orden` | `FECHA_ASC`, `FECHA_DESC`, `PRECIO_ASC`, `PRECIO_DESC`, `RELEVANCIA` | Default `FECHA_ASC` |
| `page`, `size` | entero | Paginación |

Solo devuelve eventos en `PUBLICADO` o `AGOTADO`. Los caracteres `%`, `_` y `\` se tratan como literales (no como comodines de `LIKE`).

**200**

```json
{
  "content": [
    {
      "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
      "nombre": "Festival de Verano",
      "tipo": "FESTIVAL",
      "ciudad": "Tandil",
      "lugarNombre": "Estadio Municipal",
      "fecha": "2027-01-15T21:00:00-03:00",
      "estado": "PUBLICADO",
      "altaDemanda": true,
      "precioDesde": 8000.00
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Errores: `400 INVALID_DATE_RANGE` (`fechaDesde` posterior a `fechaHasta`), `400 INVALID_SORT`. La respuesta incluye `X-Cache: HIT|MISS|BYPASS` según el estado de la caché de Redis; el TTL se configura con `CATALOG_CACHE_SEARCH_TTL` (default `30s`) y la búsqueda se invalida al publicar, editar, cancelar o cambiar precios.

### 3.11 Detalle de evento — `GET /api/catalog/events/{eventoId}` (público)

**200**

```json
{
  "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "lugarNombre": "Estadio Municipal",
  "ciudad": "Tandil",
  "organizadorId": "2b3c...",
  "fecha": "2027-01-15T21:00:00-03:00",
  "estado": "PUBLICADO",
  "limitePorCliente": 4,
  "altaDemanda": true,
  "participantes": ["Artista A", "Artista B"],
  "sectores": [
    {
      "sectorId": "9a1b...",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "disponibles": 198
    }
  ]
}
```

`disponibles` se consulta a inventory en el momento. La respuesta informa `X-Cache: HIT|MISS|BYPASS` para la parte estática del detalle; la disponibilidad y los asientos nunca se cachean. Errores: `404 EVENT_NOT_FOUND` (inexistente o en `BORRADOR`), `503 INVENTORY_UNAVAILABLE`.

### 3.12 Interno — `PATCH /internal/events/{eventoId}/availability`

Llamado por inventory cuando la disponibilidad llega a 0 o vuelve a ser positiva.

```json
{ "agotado": true }
```

**204**. Idempotente. `404 EVENT_NOT_FOUND` si el evento no existe en catalog. Con `agotado: true` se cierra la fila del evento de alta demanda (motivo `AGOTADO`); con `false` se vuelve a abrir. El scheduler de finalización cierra la fila con motivo `FINALIZADO`. Un fallo de queue no altera el `204`.

### 3.13 Interno — reporte y candidatos (X-Internal-Token)

No se exponen por el gateway. Requieren `X-Internal-Token`; sin él o con un valor inválido responden `401 UNAUTHORIZED`.

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/internal/events/{eventoId}/report` | `200` con `eventoId`, `nombre`, `organizadorId`, `lugarId`, `lugarNombre`, `ciudad`, `fecha`, `estado`, `participantes[]` y `sectores[]` (capacidad, precios y disponibles) |
| GET | `/internal/recommendations/candidates?tipo=&artista=&ciudad=&fechaDesde=&fechaHasta=&size=20` | `200` con `eventoId`, `nombre`, `tipo`, `artistas[]`, `ciudad`, `fechaEvento`, `estado` y `altaDemanda` |

Ambos solo consideran eventos `PUBLICADO` o `AGOTADO`. Errores: `400 VALIDATION_ERROR` (filtros inválidos), `401 UNAUTHORIZED`, `404 EVENT_NOT_FOUND` (reporte). Los filtros son opcionales y combinables; `size` limita la cantidad de candidatos devueltos.

### 3.14 Integración de catalog con queue-service (tarea 3.6)

Catalog consume estos endpoints internos de queue-service (no pública nuevos endpoints):

| Método | Ruta | Cuándo | Cuerpo |
|---|---|---|---|
| POST | `/internal/events/{eventoId}` | Publicar, editar un evento ya publicado o volver de `AGOTADO` | — |
| POST | `/internal/events/{eventoId}/close` | Cancelar (`CANCELADO`), agotar (`AGOTADO`) o finalizar (`FINALIZADO`) | `{"motivo":"CANCELADO"}` |

Ambas llamadas envían `X-Internal-Token` y propagan `X-Correlation-Id` cuando está disponible (origen: `QUEUE_INTERNAL_TOKEN`, por defecto el mismo valor que `INTERNAL_TOKEN`). Usan `QUEUE_CONNECT_TIMEOUT_MS` (default 2000) y `QUEUE_READ_TIMEOUT_MS` (default 3000) y hasta 3 intentos con backoff ante timeout, pérdida de conexión o 5xx; los 4xx no se reintentan. Solo se invocan si `altaDemanda: true`: un evento normal nunca llama a queue. Si queue-service está caído, catalog registra un warning y continúa la operación (fallback); la apertura/cierre se reintenta en la siguiente operación idempotente o con el scheduler. El contrato de la fila está en [`fila-virtual/spec.md`](../openspec/changes/entrega-2-completo/specs/fila-virtual/spec.md).

### 3.15 Eventos de dominio y outbox (tarea 3.2)

Catalog publica `evento.publicado`, `evento.actualizado` y `evento.cancelado` en el exchange topic `ticketya.events`, usando el `type` como routing key. Cada mensaje se escribe primero en `outbox_event` dentro de la transacción que cambia el evento, con este sobre:

```json
{
  "eventId": "e0000000-0000-4000-8000-000000000002",
  "type": "evento.publicado",
  "occurredAt": "2026-11-16T12:00:00Z",
  "correlationId": "5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f",
  "version": 1,
  "payload": { "eventoId": "d0000000-0000-4000-8000-000000000001" }
}
```

El ejemplo completo del payload publicado está en [`ejemplos/evento-publicado.json`](../docs/ejemplos/evento-publicado.json) y es el mismo que consume notification-service (§8). `eventId` es único y `correlationId` preserva la cabecera `X-Correlation-Id` de la petición que originó el cambio.

El publicador procesa `CATALOG_OUTBOX_BATCH_SIZE` eventos (default `50`) cada `CATALOG_OUTBOX_PUBLISH_MS` (default `2000`). Ante un fallo de RabbitMQ incrementa `attempts`, persiste `last_error` y reprograma con backoff lineal de `CATALOG_OUTBOX_BACKOFF_SECONDS × attempts` (base default `5`). Tras `CATALOG_OUTBOX_MAX_ATTEMPTS` (default `10`) el evento pasa a `FAILED` y no se reintenta; la operación de negocio ya confirmada no se revierte. Los estados `PENDING`, `PUBLISHED` y `FAILED` están definidos por `V6__outbox_event.sql` y `V8__outbox_failed_status.sql`.

### 3.16 Auditoría (tarea 9.2)

Publicar, editar y cancelar un evento escriben una fila en `audit_log` (`V7__audit_log.sql`) con `evento_id`, `accion` (`PUBLICAR_EVENTO`, `EDITAR_EVENTO`, `CANCELAR_EVENTO`), usuario, rol, `resultado` (`EXITOSO`/`RECHAZADO`), detalle y `correlation_id` (propagado desde `X-Correlation-Id`). La escritura es best-effort y corre fuera de la transacción de negocio: si la bitácora falla se registra un warning y la operación principal no se revierte. Las peticiones sin identidad se rechazan antes de auditar.

## 4. inventory-service

OpenAPI: [`../openapi/inventory-openapi.json`](../openapi/inventory-openapi.json). Swagger UI en `http://localhost:8082/swagger-ui.html`.

### 4.0 Interno — estado del evento

`PATCH /internal/events/{eventoId}/state`

Lo llama catalog cuando cancela o finaliza un evento. Todos los `/internal/**` de inventory exigen `X-Internal-Token`. Sin el header, o con un valor distinto, responden `401 UNAUTHORIZED`.

```json
{ "estado": "CANCELADO" }
```

Estados aceptados: `CANCELADO`, `FINALIZADO`. **204**, también si ya estaba en ese estado. `404 EVENT_NOT_FOUND`. `400 VALIDATION_ERROR` si el estado no es uno de esos dos. `409 EVENT_NOT_EDITABLE` si el evento ya está en el otro estado terminal.

### 4.1 Disponibilidad — `GET /api/inventory/events/{eventoId}/availability` (público)

**200**

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "estado": "PUBLICADO",
  "sectores": [
    {
      "sectorId": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "disponibles": 198
    },
    {
      "sectorId": "7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d9e0f",
      "nombre": "Campo",
      "tipo": "NO_NUMERADO",
      "precio": 8000.00,
      "capacidad": 5000,
      "disponibles": 4998
    }
  ]
}
```

`disponibles` en sectores numerados es la cantidad de asientos `LIBRE`.

Errores: `404 EVENT_NOT_FOUND`.

### 4.2 Mapa de asientos — `GET /api/inventory/events/{eventoId}/seats?sectorId={uuid}` (público)

**200**

```json
[
  { "asientoId": "a1b2...", "fila": 1, "numero": 1, "estado": "LIBRE" },
  { "asientoId": "a1b2...", "fila": 1, "numero": 2, "estado": "RESERVADO" }
]
```

Errores: `400 SECTOR_NOT_FROM_EVENT`, `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`. Para sectores no numerados devuelve lista vacía.

### 4.3 Reservar — `POST /api/inventory/reservations` (CLIENT)

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "items": [
    { "sectorId": "9a1b...", "asientoIds": ["a1b2...", "a1b3..."] },
    { "sectorId": "7c8d...", "cantidad": 2 }
  ]
}
```

Reglas:

- Un item con `asientoIds` es de sector numerado; `cantidad` debe omitirse.
- Un item con `cantidad` es de zona no numerada; `asientoIds` debe omitirse.
- Todo o nada: si un asiento no está libre o la zona no alcanza, no se reserva nada.
- `clienteId` sale de `X-User-Id`.
- Si el evento es de alta demanda, el header `X-Queue-Token` es obligatorio. Es el JWT que entrega queue al habilitar al cliente (`type=QUEUE`, claims `eventId`, `sub`, `jti`, `exp`), firmado con `QUEUE_TOKEN_SECRET`. Inventory lo valida en local y no llama a queue en la reserva. Sin token: `403 QUEUE_REQUIRED`. Token vencido, de otro evento, de otro cliente o con firma inválida: `403 QUEUE_TOKEN_INVALID` y no se toca el cupo. El token se puede reutilizar hasta `exp`. Un evento normal no lo exige. Cuando ese evento queda `AGOTADO`, inventory cierra la fila con `POST /internal/events/{eventoId}/close` y motivo `AGOTADO`; no la vuelve a abrir.
- Límite por cliente: se toma `pg_advisory_xact_lock(hashtext(eventoId || clienteId))` y se suman `cliente_evento.entradas_vendidas` más las cantidades de reservas `VIGENTE`. Si eso más lo pedido supera `limitePorCliente`, se responde 422 y no se reserva nada.
- Si el evento es de alta demanda, el header `X-Queue-Token` es obligatorio. Inventory valida el JWT en local (firma HS256, `exp`, `eventId`, `sub` y `type=QUEUE`) y no llama a queue. Sin token: `403 QUEUE_REQUIRED`. Token vencido, de otro cliente, de otro evento o con otra firma: `403 QUEUE_TOKEN_INVALID`. El token no es de un solo uso: se puede reutilizar hasta su `exp` (5 minutos). No hay unique en `reserva.queue_jti`. El límite por cliente y la no doble venta siguen aplicando.
- Vencimiento: 10 minutos desde la creación.

**201 Created**

```json
{
  "id": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "estado": "VIGENTE",
  "expiresAt": "2026-11-02T18:10:00-03:00",
  "items": [
    {
      "sectorId": "9a1b...",
      "asientoId": "a1b2...",
      "cantidad": 1,
      "precioUnitario": 15000.00,
      "subtotal": 15000.00
    },
    {
      "sectorId": "7c8d...",
      "asientoId": null,
      "cantidad": 2,
      "precioUnitario": 8000.00,
      "subtotal": 16000.00
    }
  ],
  "total": 31000.00
}
```

Errores: `400 VALIDATION_ERROR`, `400 SECTOR_MISMATCH` (el asiento no pertenece al sector del ítem), `401 UNAUTHORIZED`, `403 QUEUE_REQUIRED`, `403 QUEUE_TOKEN_INVALID`, `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`, `409 SEAT_NOT_AVAILABLE`, `409 INSUFFICIENT_AVAILABILITY`, `409 EVENT_NOT_ON_SALE` (cancelado), `409 EVENT_FINISHED` (la fecha del evento ya pasó), `422 CLIENT_LIMIT_EXCEEDED`, `503 LOCK_TIMEOUT` (con `Retry-After: 1`).

`403 QUEUE_REQUIRED`

```json
{
  "type": "https://ticketya.local/errors/queue-required",
  "title": "Forbidden",
  "status": 403,
  "detail": "Este evento exige un token de fila vigente",
  "instance": "/api/inventory/reservations",
  "code": "QUEUE_REQUIRED",
  "errors": []
}
```

`409 SEAT_NOT_AVAILABLE`

```json
{
  "type": "https://ticketya.local/errors/seat-not-available",
  "title": "Conflict",
  "status": 409,
  "detail": "El asiento c0000000-0000-4000-8000-010101000000 ya no está disponible",
  "instance": "/api/inventory/reservations",
  "code": "SEAT_NOT_AVAILABLE",
  "errors": []
}
```

### 4.4 Consultar reserva — `GET /api/inventory/reservations/{reservaId}` (CLIENT dueño)

**200** `ReservationResponse`. Si está vencida libera los lugares y devuelve `estado: "VENCIDA"` (no 410 en la consulta; 410 se usa al pagar). Errores: `403 NOT_RESERVATION_OWNER`, `404 RESERVATION_NOT_FOUND`.

### 4.5 Cancelar reserva — `DELETE /api/inventory/reservations/{reservaId}` (CLIENT dueño)

**204 No Content** si estaba `VIGENTE` (pasa a `CANCELADA` y libera asientos y cupo) o si ya estaba `CANCELADA` (no libera otra vez).

Errores: `401 UNAUTHORIZED`, `403 NOT_RESERVATION_OWNER`, `404 RESERVATION_NOT_FOUND`, `409 RESERVATION_NOT_CANCELLABLE` (confirmada o en un estado que no se cancela), `410 RESERVATION_EXPIRED`.

`403 NOT_RESERVATION_OWNER`

```json
{
  "type": "https://ticketya.local/errors/not-reservation-owner",
  "title": "Forbidden",
  "status": 403,
  "detail": "La reserva pertenece a otro cliente",
  "instance": "/api/inventory/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "code": "NOT_RESERVATION_OWNER",
  "errors": []
}
```

`409 RESERVATION_NOT_CANCELLABLE`

```json
{
  "type": "https://ticketya.local/errors/reservation-not-cancellable",
  "title": "Conflict",
  "status": 409,
  "detail": "La reserva ya está confirmada: hay que devolverla",
  "instance": "/api/inventory/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "code": "RESERVATION_NOT_CANCELLABLE",
  "errors": []
}
```

`410 RESERVATION_EXPIRED`

```json
{
  "type": "https://ticketya.local/errors/reservation-expired",
  "title": "Gone",
  "status": 410,
  "detail": "La reserva está vencida y no se puede cancelar",
  "instance": "/api/inventory/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "code": "RESERVATION_EXPIRED",
  "errors": []
}
```

### 4.6 Interno — alta/actualización de inventario

`POST /internal/events/{eventoId}/inventory`

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "fecha": "2027-01-15T21:00:00-03:00",
  "altaDemanda": true,
  "limitePorCliente": 4,
  "version": 2,
  "sectores": [
    {
      "sectorId": "9a1b...",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "filas": 10,
      "asientos": [
        { "asientoId": "a1b2...", "fila": 1, "numero": 1 }
      ]
    }
  ]
}
```

**200**

```json
{ "eventoId": "1f2e...", "sectores": 1, "asientos": 200, "idempotente": true }
```

Idempotente por `eventoId` + `version`: reintentar no duplica filas ni reinicia disponibilidad ya vendida. Catalog incrementa `version` en cada publicación o edición de un evento publicado. `altaDemanda` queda persistido: si es `true`, la reserva pública exige `X-Queue-Token`.

### 4.6.1 Interno — cambiar estado del evento

`PATCH /internal/events/{eventoId}/state`

```json
{ "estado": "CANCELADO" }
```

**204 No Content**. Catalog lo llama antes de cancelar (`CANCELADO`) o finalizar (`FINALIZADO`) para que inventory deje de vender. Idempotente.

### 4.7 Interno — consultar reserva para orders

`GET /internal/reservations/{reservaId}` → **200** `ReservationResponse`; `404 RESERVATION_NOT_FOUND`.

Además de los campos de la consulta pública (4.4), `ReservationResponse` incluye `eventoNombre` y `eventoFecha` para que orders pueda guardar el snapshot del evento sin volver a llamar a catalog:

```json
{
  "id": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "estado": "VIGENTE",
  "expiresAt": "2026-11-02T18:10:00-03:00",
  "items": [],
  "total": 0.00
}
```

### 4.8 Interno — confirmar venta

`POST /internal/reservations/{reservaId}/confirm` → **200** reserva `CONFIRMADA`, asientos `VENDIDO`.

Errores: `409 RESERVATION_NOT_CONFIRMABLE`, `410 RESERVATION_EXPIRED`. Idempotente: repetir sobre una reserva ya `CONFIRMADA` devuelve 200.

### 4.9 Interno — liberar reserva

`POST /internal/reservations/{reservaId}/release`

```json
{ "motivo": "PAGO_RECHAZADO" }
```

**204**, idempotente.

### 4.10 Interno — liberar por devolución

`POST /internal/returns`

```json
{
  "eventoId": "1f2e...",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "referencia": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "items": [
    { "sectorId": "9a1b...", "asientoId": "a1b2...", "cantidad": 1 }
  ]
}
```

**204**, idempotente. Libera asientos vendidos o repone disponibilidad de zonas.
`referencia` es opcional; orders envía el id de la devolución para que un reintento
no reponga dos veces la disponibilidad de una zona no numerada.

Header opcional `Idempotency-Key`. Si orders reintenta la misma devolución, manda la misma clave: la segunda llamada no vuelve a sumar lugares. Sin la clave, un asiento igual no se libera dos veces (solo pasa de `VENDIDO` a `LIBRE` una vez); la zona sí necesita la clave para no reponerse dos veces.

`clienteId` lo manda orders con el dueño de la entrada. En una zona, además de sumar `disponibles`, inventory resta esa cantidad de `cliente_evento.entradas_vendidas` (`GREATEST(0, …)`). Si hay algún ítem de zona (`asientoId` null) y falta `clienteId`, responde `400 CLIENT_ID_REQUIRED` y no aplica la devolución. Un asiento vendido sigue liberando el cupo con el cliente de su reserva, aunque `clienteId` falte. Errores: `400 CLIENT_ID_REQUIRED`, `400 VALIDATION_ERROR`.

## 5. orders-service

### 5.1 Crear orden y pagar — `POST /api/orders` (autenticado)

Headers: `Idempotency-Key: <clave>`.

```json
{
  "reservaId": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "medioPago": "TARJETA",
  "token": "APROBADO"
}
```

`token` es el resultado simulado de la pasarela: `APROBADO`, `RECHAZADO` o `LENTO`.

**201 Created** (también cuando el pago se rechaza: la orden existe en `RECHAZADA`)

```json
{
  "id": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
  "clienteId": "2b3c...",
  "reservaId": "b1c2...",
  "eventoId": "1f2e...",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "estado": "PAGADA",
  "medioPago": "TARJETA",
  "total": 31000.00,
  "items": [
    { "sectorId": "9a1b...", "asientoId": "a1b2...", "cantidad": 1, "precioUnitario": 15000.00, "subtotal": 15000.00 }
  ],
  "entradas": [
    { "id": "d1e2...", "qrCode": "e1f2...", "estado": "VALIDA", "sectorId": "9a1b...", "asientoId": "a1b2..." }
  ],
  "createdAt": "2026-11-02T18:00:05-03:00"
}
```

Errores: `400 VALIDATION_ERROR`, `403 NOT_RESERVATION_OWNER`, `404 RESERVATION_NOT_FOUND`, `409 IDEMPOTENCY_IN_PROGRESS` (con `Retry-After`), `409 RESERVATION_ALREADY_PAID` (la reserva ya tiene una orden; índice único `uq_orden_reserva`), `410 RESERVATION_EXPIRED`, `422 IDEMPOTENCY_KEY_REUSED` (misma clave, cuerpo distinto), `502 PAYMENT_GATEWAY_ERROR`.

### 5.2 Listar mis órdenes — `GET /api/orders?page=0&size=20` (autenticado)

**200** paginado de `OrderResponse` del cliente autenticado, más recientes primero.

### 5.3 Detalle de orden — `GET /api/orders/{ordenId}` (dueño)

**200** `OrderResponse`; `403 ORDER_NOT_OWNER`, `404 ORDER_NOT_FOUND`.

### 5.4 Mis entradas — `GET /api/orders/tickets?eventoId={uuid}&page=0&size=20` (autenticado)

**200** paginado:

```json
{
  "content": [
    {
      "id": "d1e2...",
      "eventoId": "1f2e...",
      "eventoNombre": "Festival de Verano",
      "eventoFecha": "2027-01-15T21:00:00-03:00",
      "sectorId": "9a1b...",
      "asientoId": "a1b2...",
      "qrCode": "e1f2...",
      "estado": "VALIDA",
      "createdAt": "2026-11-02T18:00:05-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 5.5 Detalle de entrada — `GET /api/orders/tickets/{entradaId}` (dueño)

**200** `TicketResponse`; `403 NOT_TICKET_OWNER`, `404 TICKET_NOT_FOUND`.

### 5.6 Solicitar devolución — `POST /api/orders/tickets/{entradaId}/returns` (dueño)

```json
{ "motivo": "No puedo asistir" }
```

**201 Created**

```json
{
  "id": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "entradaId": "d1e2...",
  "estado": "PENDIENTE",
  "motivo": "No puedo asistir",
  "createdAt": "2026-11-02T18:05:00-03:00",
  "resolvedAt": null
}
```

Errores: `403 NOT_TICKET_OWNER`, `404 TICKET_NOT_FOUND`, `409 TICKET_NOT_VALID` (utilizada/devuelta/en reintegro/en devolución), `409 RETURN_ALREADY_EXISTS`, `422 RETURN_WINDOW_CLOSED` (faltan 72 h o menos).

### 5.7 Aprobar devolución — `POST /api/orders/returns/{devolucionId}/approve` (ADMIN)

Sin cuerpo. **200** `ReturnResponse` con `estado: "APROBADA"`. Marca la entrada `EN_DEVOLUCION`, solicita el reintegro y libera el lugar en inventory.

Si falla la pasarela o inventory, responde `502` (`PAYMENT_GATEWAY_ERROR` o `INVENTORY_UNAVAILABLE`), la devolución queda `PROCESANDO` y la entrada `EN_DEVOLUCION`; los schedulers de reintento la terminan.

Errores: `404 RETURN_NOT_FOUND`, `409 RETURN_ALREADY_RESOLVED`, `502 PAYMENT_GATEWAY_ERROR`/`INVENTORY_UNAVAILABLE` (queda `PROCESANDO` y se reintenta).

### 5.8 Rechazar devolución — `POST /api/orders/returns/{devolucionId}/reject` (ADMIN)

Sin cuerpo. **200** `ReturnResponse` con `estado: "RECHAZADA"`; la entrada sigue `VALIDA`.

Errores: `404 RETURN_NOT_FOUND`, `409 RETURN_ALREADY_RESOLVED`, `409 RETURN_IN_PROGRESS` (la devolución está `PROCESANDO` y no se puede rechazar).

### 5.9 Interno — cancelar evento

`POST /internal/events/{eventoId}/cancel` (llamado por catalog)

**200**

```json
{ "eventoId": "1f2e...", "entradasAfectadas": 120, "reintegrosSolicitados": 120 }
```

Marca las entradas vendidas como `EN_REINTEGRO` y solicita el refund. Idempotente: repetir devuelve las mismas entradas ya afectadas sin duplicar reintegros.

### 5.10 Validar QR de acceso — `POST /api/orders/access/validate` (ACCESS_STAFF o ADMIN)

```json
{ "qrCode": "235e9e9e-3451-4605-94a1-ae9cd55aef18", "eventoId": "1f2e..." }
```

**200 OK**

```json
{
  "entradaId": "c6b2...",
  "ordenId": "c1d2...",
  "eventoId": "1f2e...",
  "sectorId": "9a1b...",
  "estado": "UTILIZADA",
  "usedAt": "2026-11-20T21:00:00-03:00",
  "usedBy": "55555555-5555-4555-8555-555555555555"
}
```

El consumo es un `UPDATE` condicional atómico sobre `entrada` (`estado = 'VALIDA'` y
`used_at IS NULL`); dos peticiones concurrentes con el mismo QR producen un `200` y un `409`.
Cada intento se registra en `audit_log` como `INGRESO_VALIDADO` o `INGRESO_RECHAZADO` con
`X-Correlation-Id`; el fallo de la auditoría no revierte la validación.

Errores: `400 VALIDATION_ERROR`, `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 TICKET_NOT_FOUND`,
`409 TICKET_ALREADY_USED`, `409 TICKET_WRONG_EVENT`, `409 TICKET_NOT_VALID`.

### 5.11 Reporte del evento — `GET /api/orders/reports/events/{eventoId}` (ORGANIZER dueño o ADMIN)

**200 OK**

```json
{
  "eventoId": "1f2e...",
  "organizadorId": "2a7c...",
  "sectores": [
    {
      "sectorId": "9a1b...",
      "sectorNombre": "Platea A",
      "capacidad": 100,
      "vendidas": 40,
      "devueltas": 5,
      "netas": 35,
      "ocupacion": 0.35,
      "recaudacion": 525000.00
    }
  ],
  "totales": { "vendidas": 40, "devueltas": 5, "netas": 35, "recaudacion": 525000.00 },
  "datosDesactualizados": false,
  "generadoEn": "2026-11-20T21:00:00-03:00"
}
```

`ocupacion` es `netas / capacidad` (0.35 = 35 %) y es `null` si no se conoce la capacidad.
`vendidas` incluye entradas devueltas después de la compra; `recaudacion` descuenta los
reintegros aprobados. Un evento sin ventas devuelve ceros y ocupación `0`.

La capacidad, el nombre de sector y el organizador se obtienen de `GET /internal/events/{eventoId}/report`
(§3.13) con `X-Internal-Token`, con cache en memoria de 60 s. Si catalog no responde se usa la caché o el snapshot de `orden` y se marca
`datosDesactualizados: true`; sin dueño verificable se responde `503 CATALOG_UNAVAILABLE`.

Errores: `401 UNAUTHORIZED`, `403 FORBIDDEN` (rol no habilitado), `403 NOT_EVENT_OWNER` (otro
organizador), `404 EVENT_NOT_FOUND`, `503 CATALOG_UNAVAILABLE`.

## 6. recommendation-service

Base MongoDB `recommendation_db`. Las preferencias son del cliente autenticado: `GET` y `PUT` solo aceptan rol `CLIENT` y leen/escriben el `clienteId` derivado de `X-User-Id`. El servicio no acepta que el body imponga otro cliente.

> Mientras P4 no agregue `/api/recommendations/**` al gateway, los ejemplos de esta sección se prueban directo contra `http://localhost:8084`. El contrato final sigue siendo por el gateway.

### 6.1 Consultar preferencias — `GET /api/recommendations/preferences` (CLIENT)

Sin parámetros. Si el cliente nunca registró preferencias responde `200` con la forma vacía documentada.

```json
{
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "tipos": ["FESTIVAL", "RECITAL"],
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "actualizadoEn": "2026-11-14T12:00:00Z"
}
```

```json
{
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "tipos": [],
  "artistas": [],
  "ciudad": null,
  "actualizadoEn": null
}
```

### 6.2 Guardar preferencias — `PUT /api/recommendations/preferences` (CLIENT)

Upsert idempotente: el primer `PUT` crea el documento y los siguientes lo actualizan sin duplicarlo.

```json
{
  "tipos": ["RECITAL", "FESTIVAL"],
  "artistas": ["Los Piojos"],
  "ciudad": "Tandil"
}
```

Reglas:

- `tipos[]` admite `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL` y `OTRO`. Un valor desconocido responde `400 INVALID_EVENT_TYPE` con `errors[].field = tipos[i]`.
- `ciudad` es obligatoria (`400 VALIDATION_ERROR` si falta o queda vacía).
- Se recortan espacios y se eliminan duplicados sin distinguir mayúsculas.
- Un `clienteId` en el body distinto del autenticado responde `403 NOT_PREFERENCE_OWNER`.
- Respuesta `200` con el documento guardado y `actualizadoEn`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ticketya.test","password":"demo"}' | jq -r .token)

curl -X PUT http://localhost:8080/api/recommendations/preferences \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tipos":["RECITAL"],"artistas":["Los Piojos"],"ciudad":"Tandil"}'
```

### 6.3 Interno — `GET /internal/preferences/{clienteId}` (X-Internal-Token)

Lo consume notification-service para decidir si avisa por artista favorito. Responde `200` con las preferencias del cliente o la forma vacía, y `401 UNAUTHORIZED` sin token o con token inválido. No se expone por el gateway.

```bash
curl http://localhost:8084/internal/preferences/33333333-3333-4333-8333-333333333333 \
  -H "X-Internal-Token: ticketya-internal-dev"
```

### 6.3.1 Interno — `GET /internal/preferences?artista={artista}&ciudad={ciudad}` (X-Internal-Token)

Lo consume notification-service (8.3) para encontrar clientes que tienen un artista favorito en la ciudad de un `evento.publicado`. Devuelve `200` con la lista de coincidencias y `[]` si no hay ninguna; `401 UNAUTHORIZED` sin token. Compara artistas sin distinguir mayúsculas y ciudad exacta (recortando espacios). No se expone por el gateway.

```json
[
  {
    "clienteId": "33333333-3333-4333-8333-333333333333",
    "clienteEmail": "cliente@example.com"
  }
]
```

`clienteEmail` es opcional; notification-service usa su sender mock si no viene. Implementado en recommendation-service: la búsqueda compara artistas sin distinguir mayúsculas, filtra por ciudad y devuelve solo `clienteId` (el email queda en `null`).

### 6.4 Recomendaciones — `GET /api/recommendations` (CLIENT)

Devuelve eventos publicados y futuros ordenados por afinidad. Excluye lo ya comprado, lo cancelado y lo pasado. Cada ítem incluye los motivos y el puntaje usado para ordenar.

```json
{
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "generadoEn": "2026-11-15T13:26:54Z",
  "recomendaciones": [
    {
      "eventoId": "d0000000-0000-4000-8000-000000000002",
      "nombre": "El Avaro — Teatro Universitario",
      "tipo": "TEATRO",
      "artistas": ["Compañía Teatral Tandil"],
      "ciudad": "Tandil",
      "fechaEvento": "2027-04-18T23:30:00Z",
      "altaDemanda": false,
      "puntaje": 6,
      "motivos": [
        "En tu ciudad: Tandil",
        "Popular: 2 compra(s) registrada(s)",
        "Quienes compraron tus eventos también compraron este"
      ]
    }
  ]
}
```

- Cliente sin preferencias ni historial: recibe los populares de su ciudad o los generales si no declaró ciudad.
- Sin candidatos: `200` con `recomendaciones: []`.
- Catalog caído o sin el endpoint interno de 3.5: se usa el snapshot `eventos_vistos` y se registra la degradación.
- `401` sin identidad y `403` para roles distintos de `CLIENT`.

### 6.5 Eventos similares — `GET /api/recommendations/events/{eventId}/similar` (CLIENT)

Devuelve los eventos que compraron otros clientes que también compraron el evento consultado
(“quienes compraron este también compraron”), ordenados por cantidad de compradores en común.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ticketya.test","password":"demo"}' | jq -r .token)

curl http://localhost:8080/api/recommendations/events/d0000000-0000-4000-8000-000000000001/similar \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "generadoEn": "2026-11-16T13:00:00Z",
  "similares": [
    {
      "eventoId": "d0000000-0000-4000-8000-000000000002",
      "nombre": "El Avaro — Teatro Universitario",
      "tipo": "TEATRO",
      "artistas": ["Compañía Teatral Tandil"],
      "ciudad": "Tandil",
      "fechaEvento": "2027-04-18T23:30:00Z",
      "compradoresEnComun": 3
    }
  ]
}
```

- `compradoresEnComun` cuenta clientes distintos: varias órdenes del mismo cliente cuentan una vez.
- Se excluyen el evento consultado, los que el cliente ya compró, los cancelados/finalizados y los pasados.
- La metadata sale del snapshot `eventos_vistos`; un evento sin snapshot no se lista.
- Sin otros compradores o sin candidatos elegibles: `200` con `similares: []`.
- `400 VALIDATION_ERROR` si `eventId` no es un UUID; `401` sin identidad y `403` para roles distintos de `CLIENT`.

### 6.6 Evento consumido — `orden.pagada`

recommendation-service consume el sobre de la tarea 1.5 desde `ticketya.events` con routing key `orden.pagada`:

```json
{
  "eventId": "e0000000-0000-4000-8000-000000000001",
  "type": "orden.pagada",
  "occurredAt": "2026-11-15T12:00:00Z",
  "correlationId": "5d1f...",
  "version": 1,
  "payload": {
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
}
```

El historial es idempotente por `(eventId, clienteId, eventoId)`. Un evento duplicado no se registra dos veces; `orden.reintegrada` y los estados distintos de `PAGADA` no entran al historial. La cola `recommendation.history` tiene DLQ `recommendation.history.dlq`.

El contrato canónico del sobre y de todos los payloads está en [`eventos.md`](eventos.md), con el fixture [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json). orders debe publicar `eventoTipo`, `artistas` y `ciudad` desde el snapshot de la orden (ADR-017 y migración 6.1); no se acepta que recommendation los consulte a catalog al consumir.

## 7. queue-service

No hay WebSockets. El cliente hace polling de `GET /api/queue/events/{eventoId}/position` cada 2 a 5 segundos. La habilitación es por tandas: cada `intervaloSegundos` (30 por defecto) salen los primeros `batchSize` clientes (50 por defecto), en orden de llegada. Abrir y cerrar son internos y no pasan por el gateway.

### 7.1 Abrir — `POST /internal/events/{eventoId}`

Lo llama catalog al publicar un evento de alta demanda. Header `X-Internal-Token`. Sin cuerpo.

**201** si la fila se crea, **200** si ya existía o se reabre. Repetirlo no pisa `batchSize` ni `intervaloSegundos`. Si estaba `CERRADA` por `AGOTADO`, vuelve a `ABIERTA` y se le quita el TTL de retención (se agregó capacidad). Si estaba `CERRADA` por `CANCELADO` o `FINALIZADO`, sigue cerrada. Al cerrar, las claves del evento reciben `QUEUE_RETENTION` (7 días por defecto).

```json
{ "eventoId": "11111111-1111-4111-8111-111111111111", "estado": "ABIERTA", "batchSize": 50, "intervaloSegundos": 30 }
```

### 7.2 Ingresar — `POST /api/queue/events/{eventoId}/join` (CLIENT)

Headers `X-User-Id` y `X-User-Role`.

**201** en el primer ingreso, **200** si el cliente ya estaba. La posición no cambia (`ZADD NX`).

```json
{
  "eventoId": "11111111-1111-4111-8111-111111111111",
  "posicion": 1,
  "adelante": 0,
  "estado": "EN_ESPERA",
  "estimacionSegundos": 30
}
```

Errores: `401 UNAUTHORIZED`, `409 QUEUE_NOT_OPEN` (el evento no tiene fila), `409 QUEUE_CLOSED` (la fila ya no admite ingresos).

### 7.3 Posición — `GET /api/queue/events/{eventoId}/position` (CLIENT)

Misma identidad que el ingreso. Es el request que el cliente repite.

- `EN_ESPERA`: `posicion` (`ZRANK + 1`), `adelante` y `estimacionSegundos` = `(adelante / batchSize + 1) * intervaloSegundos`.
- `HABILITADO`: `posicion` 0 y `token`, el JWT de compra. No es de un solo uso: vale hasta `exp` (5 minutos) y se puede reutilizar. No hay unique en `queue_jti`.
- `CERRADA`: sin token. La fila se cerró mientras el cliente estaba adentro.

**404** `NOT_IN_QUEUE` si nunca ingresó o si el token ya venció.

### 7.4 Cerrar — `POST /internal/events/{eventoId}/close`

Header `X-Internal-Token`. Cuerpo `{ "motivo": "CANCELADO" | "FINALIZADO" | "AGOTADO" }`.

**200** al cerrar y al repetir el cierre. El motivo original se conserva. **404** si la fila nunca se abrió. **400** si el motivo no es uno de esos tres.

## 8. notification-service

OpenAPI: [`../openapi/notification-openapi.json`](../openapi/notification-openapi.json). Swagger UI en `http://localhost:8085/swagger-ui.html`. El documento incluye los esquemas `bearerAuth`, `X-User-Id`, `X-User-Role` y `X-Correlation-Id`, además de `ProblemDetail` con ejemplos para `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOTIFICATION_FORBIDDEN` y `NOTIFICATION_NOT_FOUND`.

Los estados `PENDIENTE`, `ENVIADA` y `FALLIDA` reflejan el ciclo de envío. El primer envío ocurre al consumir el evento; si falla, la notificación queda `PENDIENTE` con `proximoReintento` calculado con backoff lineal (`NOTIFICATION_BACKOFF_SECONDS × intentos`). `reenviarPendientes()` reintenta cada `NOTIFICATION_RETRY_POLL_MS` (default `10000`) y tras `NOTIFICATION_MAX_ATTEMPTS` (default `3`) la marca `FALLIDA`. Una notificación ya enviada o fallida no se reenvía.

### 8.1 Listar mis notificaciones — `GET /api/notifications` (CLIENT)

Devuelve solo las notificaciones del cliente autenticado (`X-User-Id`), ordenadas por `createdAt` descendente. Parámetros: `page` (desde 0, default 0), `size` (default 20, máximo 100) y `estado` opcional (`PENDIENTE`, `ENVIADA` o `FALLIDA`).

```json
{
  "content": [
    {
      "id": "a0000000-0000-4000-8000-000000000001",
      "eventoId": "d0000000-0000-4000-8000-000000000001",
      "tipo": "orden.pagada",
      "asunto": "Compra confirmada",
      "contenido": "Tu compra para Festival de Rock Tandil 2027 quedó confirmada.",
      "estado": "ENVIADA",
      "intentos": 1,
      "proximoReintento": null,
      "createdAt": "2026-11-15T12:00:00-03:00",
      "enviadaAt": "2026-11-15T12:00:01-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `401 UNAUTHORIZED` sin identidad y `403 FORBIDDEN` para roles distintos de `CLIENT`.
- Un `estado` desconocido responde `400 VALIDATION_ERROR`.

### 8.2 Detalle de notificación — `GET /api/notifications/{notificacionId}` (CLIENT dueño)

**200** con el mismo objeto de 8.1. Una notificación de otro cliente responde `403 NOTIFICATION_FORBIDDEN`; una inexistente, `404 NOTIFICATION_NOT_FOUND`. Un `notificacionId` con formato UUID inválido responde `400 VALIDATION_ERROR` con `errors[0].field = "notificacionId"`.

## 9. Flujo de compra de punta a punta


```bash
GATEWAY=http://localhost:8080

# 0) Login: el gateway emite el JWT. No se mandan X-User-* a mano.
TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ticketya.test","password":"demo"}' | jq -r .token)

# 1) Buscar
curl "$GATEWAY/api/catalog/events?ciudad=Tandil&orden=FECHA_ASC"

# 2) Disponibilidad y asientos
curl "$GATEWAY/api/inventory/events/$EVENTO/availability"
curl "$GATEWAY/api/inventory/events/$EVENTO/seats?sectorId=$SECTOR"

# 3) Reservar (10 minutos)
curl -X POST "$GATEWAY/api/inventory/reservations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"eventoId":"'"$EVENTO"'","items":[{"sectorId":"'"$SECTOR"'","asientoIds":["'"$ASIENTO"'"]}]}'

# 4) Pagar (idempotente)
curl -X POST "$GATEWAY/api/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: compra-$RESERVA" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"reservaId":"'"$RESERVA"'","medioPago":"TARJETA","token":"APROBADO"}'

# 5) Ver entradas
curl "$GATEWAY/api/orders/tickets" \
  -H "Authorization: Bearer $TOKEN"

# 6) Devolver una entrada (si faltan más de 72 h)
curl -X POST "$GATEWAY/api/orders/tickets/$ENTRADA/returns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"motivo":"No puedo asistir"}'
```

## 10. Catálogo de errores

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos o parámetros inválidos; incluye `errors[]` |
| 400 | `MALFORMED_JSON` | Cuerpo no parseable |
| 400 | `INVALID_DATE_RANGE` | `fechaDesde` posterior a `fechaHasta` |
| 400 | `INVALID_SORT` | `orden` no soportado |
| 400 | `INVALID_PRICE` | Precio negativo |
| 400 | `INVALID_EVENT_TYPE` | Tipo de evento desconocido en las preferencias |
| 400 | `SECTOR_NOT_FROM_VENUE` | Sector ajeno al lugar del evento |
| 400 | `SECTOR_NOT_FROM_EVENT` | Sector ajeno al evento consultado |
| 401 | `UNAUTHORIZED` | Headers ausentes, UUID inválido o rol desconocido |
| 403 | `FORBIDDEN` | Rol sin permiso para la ruta |
| 403 | `NOT_EVENT_OWNER` | El evento es de otro organizador |
| 403 | `NOT_RESERVATION_OWNER` | La reserva es de otro cliente |
| 403 | `QUEUE_REQUIRED` | El evento de alta demanda se reserva sin token de fila |
| 403 | `QUEUE_TOKEN_INVALID` | El token de fila venció, es de otro cliente o evento, o la firma no cierra |
| 403 | `ORDER_NOT_OWNER` | La orden es de otro cliente |
| 403 | `NOT_TICKET_OWNER` | La entrada es de otro cliente |
| 403 | `NOT_PREFERENCE_OWNER` | Las preferencias son de otro cliente |
| 403 | `NOTIFICATION_FORBIDDEN` | La notificación es de otro cliente |
| 404 | `VENUE_NOT_FOUND` | Lugar inexistente |
| 404 | `SECTOR_NOT_FOUND` | Sector inexistente |
| 404 | `EVENT_NOT_FOUND` | Evento inexistente o no visible |
| 404 | `RESERVATION_NOT_FOUND` | Reserva inexistente |
| 404 | `ORDER_NOT_FOUND` | Orden inexistente |
| 404 | `TICKET_NOT_FOUND` | Entrada inexistente |
| 404 | `RETURN_NOT_FOUND` | Devolución inexistente |
| 404 | `NOTIFICATION_NOT_FOUND` | Notificación inexistente |
| 404 | `NOT_IN_QUEUE` | El cliente no ingresó a esa fila o su token de fila venció |
| 404 | `NOT_FOUND` | Ruta inexistente |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP no permitido |
| 409 | `QUEUE_NOT_OPEN` | El evento no tiene fila virtual |
| 409 | `QUEUE_CLOSED` | La fila ya no admite ingresos |
| 409 | `EVENT_NOT_EDITABLE` | Evento ya publicado o en estado no editable |
| 409 | `EVENT_INCOMPLETE` | Faltan precios para publicar |
| 409 | `EVENT_NOT_CANCELLABLE` | Evento finalizado o ya cancelado |
| 409 | `EVENT_NOT_ON_SALE` | Evento cancelado/finalizado al reservar |
| 409 | `SECTOR_ALREADY_EXISTS` | Ya existe un sector con ese nombre en el lugar |
| 409 | `VENUE_HAS_PUBLISHED_EVENTS` | El lugar tiene eventos publicados; un sector nuevo rompería su edición |
| 409 | `EVENT_FINISHED` | La fecha del evento ya pasó al reservar |
| 409 | `RESERVATION_NOT_CANCELLABLE` | La reserva confirmada no se cancela: hay que devolverla |
| 409 | `SEAT_NOT_AVAILABLE` | Asiento reservado o vendido |
| 409 | `INSUFFICIENT_AVAILABILITY` | Zona sin lugares suficientes |
| 409 | `RESERVATION_NOT_CONFIRMABLE` | Reserva liberada o vencida al confirmar |
| 409 | `RESERVATION_ALREADY_PAID` | La reserva ya se usó en otra orden (índice único por `reserva_id`) |
| 409 | `TICKET_NOT_VALID` | Entrada no válida para devolución |
| 409 | `RETURN_ALREADY_EXISTS` | Ya hay una devolución abierta |
| 409 | `RETURN_ALREADY_RESOLVED` | La devolución ya fue resuelta |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Reintento mientras la compra original corre (incluye `Retry-After`) |
| 410 | `RESERVATION_EXPIRED` | Se intenta pagar o cancelar una reserva vencida |
| 422 | `CLIENT_LIMIT_EXCEEDED` | Supera el límite de entradas por cliente |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma `Idempotency-Key` con cuerpo distinto |
| 422 | `RETURN_WINDOW_CLOSED` | Faltan 72 h o menos para el evento |
| 502 | `INVENTORY_UNAVAILABLE` | catalog/orders no pudo hablar con inventory |
| 502 | `PAYMENT_GATEWAY_ERROR` | La pasarela mock falló o expiró |
| 503 | `INVENTORY_UNAVAILABLE` | No se pudo obtener disponibilidad en el detalle |
| 500 | `INTERNAL_ERROR` | Error inesperado; no expone stacktrace |

## 11. Reglas de cambio

1. Un endpoint nuevo o un cambio de payload empieza por el spec de OpenSpec y este documento.
2. El PR actualiza el OpenAPI y el README del servicio en el mismo commit.
3. Los códigos de error son estables: no se renombran sin actualizar `docs/pruebas.md` y la colección Postman.
4. Los endpoints internos no se exponen en el gateway ni en la colección pública de Postman.
5. Los ejemplos de este documento son la base de los tests de contrato y de la colección Postman (5.2).
