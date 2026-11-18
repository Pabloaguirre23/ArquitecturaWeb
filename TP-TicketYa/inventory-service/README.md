# inventory-service

Servicio de disponibilidad, reservas temporales y no doble venta de TicketYa.

- **Puerto:** 8082
- **Base:** `inventory_db` (PostgreSQL 16, puerto 5434)
- **Responsable:** P2 — ver [`docs/equipo/compa-2.md`](../docs/equipo/compa-2.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 4.

## Endpoints implementados (Día 1)

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/internal/events/{eventoId}/inventory` | interno | Alta/actualización idempotente del snapshot de catalog. Exige `X-Internal-Token` |
| PATCH | `/internal/events/{eventoId}/state` | interno | Catalog avisa `CANCELADO` o `FINALIZADO`. Idempotente, 204 |
| GET | `/api/inventory/events/{eventoId}/availability` | público | Disponibilidad por sector |
| GET | `/api/inventory/events/{eventoId}/seats?sectorId=` | público | Mapa de asientos |
| POST | `/api/inventory/reservations` | CLIENT | Reserva atómica de asientos numerados y zonas. Otro rol responde 403 |
| GET | `/api/inventory/reservations/{reservaId}` | CLIENT dueño | Consulta de una reserva. Otro rol responde 403 |
| DELETE | `/api/inventory/reservations/{reservaId}` | CLIENT dueño | Cancela una reserva vigente y libera los lugares. Repetir responde 204. Otro rol responde 403 |
| GET | `/internal/reservations/{reservaId}` | interno | Reserva para orders, con nombre/fecha del evento |
| POST | `/internal/reservations/{reservaId}/confirm` | interno | Confirma la venta. Repetir una confirmada devuelve 200 |
| POST | `/internal/reservations/{reservaId}/release` | interno | Libera la reserva si el pago se rechazó. Idempotente, 204 |
| POST | `/internal/returns` | interno | Devuelve un asiento vendido o repone una zona. Idempotente con `Idempotency-Key`, 204 |

Límite por cliente, vencimiento, confirmación/liberación y devolución corresponden a las tareas 3.4–3.8. La prueba de concurrencia corre con `mvn test` contra PostgreSQL real (Testcontainers): 100 hilos sobre el mismo asiento y varios sobre el último lugar de una zona. El OpenAPI exportado está en [`../openapi/inventory-openapi.json`](../openapi/inventory-openapi.json) e incluye el header `X-Queue-Token` y ejemplos de 403, 409 y 410.

## Reserva y no doble venta

- La reserva se ejecuta en una transacción.
- Asientos numerados: se bloquean con `SELECT ... FOR UPDATE` ordenados por `asientoId`; si alguno no está `LIBRE`, se aborta toda la reserva con `409 SEAT_NOT_AVAILABLE`. Si el asiento no pertenece al `sectorId` del ítem, `400 SECTOR_MISMATCH` y no se reserva nada.
- Si `fecha` del evento ya pasó, `409 EVENT_FINISHED` y no se reserva nada.
- Si `evento_inventario.alta_demanda` es true, la reserva exige el header `X-Queue-Token`: JWT HS256 firmado con `QUEUE_TOKEN_SECRET`, claims `eventId`, `sub`, `jti`, `exp` y `type=QUEUE`. Inventory lo valida en local y no llama a queue. Sin token responde `403 QUEUE_REQUIRED`. Token vencido, de otro evento, de otro cliente o con firma inválida responde `403 QUEUE_TOKEN_INVALID` y no toca asientos ni cupo. La reserva guarda `queue_jti` y `origen=FILA`. Un evento normal no lo exige. El token no es de un solo uso: se puede reutilizar hasta su `exp` (5 minutos). No hay lista de revocación ni unique en `queue_jti`.
- Zonas no numeradas: `UPDATE ... SET disponibles = disponibles - N WHERE disponibles >= N RETURNING disponibles`; si no hay filas, `409 INSUFFICIENT_AVAILABILITY`.
- Límite por cliente: `pg_advisory_xact_lock(hashtext(evento_id || cliente_id))` y después se suman `cliente_evento.entradas_vendidas` más las cantidades de reservas `VIGENTE`. Si eso más lo pedido supera `limite_por_cliente`, se responde `422 CLIENT_LIMIT_EXCEEDED` y no se escribe la reserva. Al confirmar una venta (tarea 3.6) hay que incrementar `entradas_vendidas`, porque la reserva pasa a `CONFIRMADA` y deja de entrar en el conteo de vigentes.
- La reserva vence a los 10 minutos (`expires_at`). Un scheduler cada 30 segundos pasa a `VENCIDA` las reservas `VIGENTE` con `expires_at` cumplido y libera sus lugares. Los asientos se sueltan con `UPDATE ... WHERE estado = 'RESERVADO' AND reserva_id = ?`, así dos instancias no liberan dos veces. Consultar una reserva vencida también la libera y responde `estado: VENCIDA`. Pagar una vencida responde `410 RESERVATION_EXPIRED` (lo valida la confirmación de la tarea 3.6).
- Confirmar: `UPDATE reserva SET estado = 'CONFIRMADA' WHERE id = ? AND estado = 'VIGENTE' AND expires_at > now()`. Los asientos pasan de `RESERVADO` a `VENDIDO` y se suma `cliente_evento.entradas_vendidas`. Si ya estaba `CONFIRMADA`, se devuelve la misma reserva. Si venció, se liberan los lugares y se responde `410 RESERVATION_EXPIRED`.
- Liberar: la reserva `VIGENTE` pasa a `LIBERADA` y se reponen asientos y zona. Repetir la llamada responde 204 sin tocar la disponibilidad otra vez.
- Cancelar: `DELETE /api/inventory/reservations/{id}` lo pide el dueño. La reserva `VIGENTE` pasa a `CANCELADA` y se reponen asientos y zona en el mismo update condicional, así repetir responde 204 sin liberar dos veces. Otro cliente recibe 403. Una confirmada recibe 409 y la venta no cambia. Una vencida recibe 410.
- Devolver: un asiento `VENDIDO` vuelve a `LIBRE` y resta 1 en `cliente_evento`. Una zona suma la cantidad a `disponibles` y, si el body trae `clienteId`, resta esa cantidad del cupo. Sin `clienteId` la zona se repone pero el límite no se libera. Con el header `Idempotency-Key`, repetir el mismo cuerpo no repone dos veces.
- Concurrencia: `ConcurrenciaPostgresTest` verifica que un asiento y el último lugar de una zona se reservan una sola vez.
- Si después de reservar, confirmar, liberar, vencer o devolver no queda ningún lugar libre, el evento pasa a `AGOTADO` y se avisa a catalog con `PATCH /internal/events/{id}/availability` y `{"agotado":true}`. Al volver a haber lugar, pasa a `PUBLICADO` y avisa con `false`. El aviso sale después del commit. Es idempotente: ante un 5xx o un timeout se reintenta hasta 3 intentos, con backoff. Si catalog sigue caído, el fallback registra la correlación y no deshace la operación local. El mismo aviso lo repite el reconciliador. Tras varios fallos el circuito se abre y las llamadas siguientes fallan rápido; cuando catalog vuelve, el circuito se cierra. Un 4xx no se reintenta ni abre el circuito.
- Si el evento es de alta demanda y queda `AGOTADO`, inventory cierra la fila con `POST /internal/events/{id}/close` y motivo `AGOTADO`. No la vuelve a abrir cuando hay lugar otra vez. Si queue no responde, el reconciliador reintenta el cierre en el próximo tick. Confirmar dos veces deja el asiento vendido una sola vez. Liberar dos veces no aumenta el cupo.
- El alta de inventario es idempotente por `eventoId + version` y usa `ON CONFLICT DO NOTHING` para los asientos.

## Modelo

`evento_inventario`, `sector_inventario`, `asiento_inventario`, `reserva`, `reserva_item` y `cliente_evento`. Flyway en [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5434/inventory_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `CATALOG_URL` | `http://localhost:8081` | Catalog para avisar `AGOTADO` / `PUBLICADO` |
| `CATALOG_CONNECT_TIMEOUT_MS` | `2000` | Timeout de conexión hacia catalog |
| `CATALOG_READ_TIMEOUT_MS` | `2000` | Timeout de lectura hacia catalog |
| `QUEUE_URL` | `http://localhost:8086` | Queue para cerrar la fila de un evento agotado |
| `QUEUE_CONNECT_TIMEOUT_MS` | `2000` | Timeout de conexión hacia queue |
| `QUEUE_READ_TIMEOUT_MS` | `2000` | Timeout de lectura hacia queue |
| `QUEUE_TOKEN_SECRET` | `ticketya-queue-dev-secret-key-32b` | Secreto HS256 del token de fila. Mínimo 32 bytes. El mismo que queue |
| `INTERNAL_TOKEN` | `ticketya-internal-dev` | Token de los endpoints `/internal/**` |

El pool de desarrollo tiene 20 conexiones, `connection-timeout` de 3 s y `lock_timeout` de 3 s en PostgreSQL. Si una reserva no consigue el lock, falla rápido en vez de quedar colgada. El detector de conexiones perdidas avisa a los 10 s.

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d inventory-db
cd inventory-service
mvn test
mvn spring-boot:run
curl http://localhost:8082/actuator/health
```

Swagger UI: <http://localhost:8082/swagger-ui.html> · OpenAPI en vivo: <http://localhost:8082/v3/api-docs>

El archivo versionado se regenera con `mvn test -Dexport.openapi=true` (lo escribe `OpenApiDocumentoTest`).

## Flujo de fila

Queue habilita al cliente y le devuelve el JWT en `GET /api/queue/events/{eventoId}/position`. Inventory no llama a queue para reservar: exige ese token solo si el evento es de alta demanda.

```bash
# Sin token, evento de alta demanda
curl -i -X POST http://localhost:8082/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e" \
  -H "X-User-Role: CLIENT" \
  -d '{"eventoId":"d0000000-0000-4000-8000-000000000001","items":[{"sectorId":"b0000000-0000-4000-8000-000000000002","cantidad":1}]}'
# 403 QUEUE_REQUIRED

# Con el token que devolvió la fila
curl -i -X POST http://localhost:8082/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e" \
  -H "X-User-Role: CLIENT" \
  -H "X-Queue-Token: $TOKEN" \
  -d '{"eventoId":"d0000000-0000-4000-8000-000000000001","items":[{"sectorId":"b0000000-0000-4000-8000-000000000002","cantidad":1}]}'
# 201. Reutilizar el mismo token hasta exp sigue sujeto al límite por cliente.

# Cancelar la reserva de otro cliente, una confirmada o una vencida
curl -i -X DELETE http://localhost:8082/api/inventory/reservations/$RESERVA \
  -H "X-User-Id: 2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e" \
  -H "X-User-Role: CLIENT"
# 403 NOT_RESERVATION_OWNER, 409 RESERVATION_NOT_CANCELLABLE o 410 RESERVATION_EXPIRED
```
