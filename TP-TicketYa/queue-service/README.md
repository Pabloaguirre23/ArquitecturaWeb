# queue-service

Fila virtual de TicketYa. Redis es el almacén primario.

- **Puerto:** 8086
- **Redis:** 7, puerto 6379, AOF activado
- **Estado:** ingreso, posición, tandas, token, cierre y contrato (tareas 4.1 a 4.4).

## Alcance actual

Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc y Spring Data Redis.

- `GET /actuator/health` reporta Redis (`redis`).
- `POST /internal/events/{eventoId}` abre la fila. Lo llama catalog al publicar un evento de alta demanda. Exige `X-Internal-Token`. Repetirlo no pisa el tamaño de tanda. Si la fila estaba `CERRADA` por `AGOTADO`, la reabre (se agregó capacidad) y le quita el TTL; si estaba `CERRADA` por `CANCELADO` o `FINALIZADO`, sigue cerrada.
- `POST /api/queue/events/{eventoId}/join` ingresa al cliente. El primer ingreso responde 201. Repetirlo responde 200 y conserva la posición (`ZADD NX`). Sin fila responde 409 `QUEUE_NOT_OPEN`.
- `GET /api/queue/events/{eventoId}/position` devuelve posición (`ZRANK + 1`), personas adelante, estado `EN_ESPERA` y una estimación. Si una tanda ya lo habilitó, el estado es `HABILITADO` y la respuesta incluye el token. Quien no ingresó, o cuyo token venció, recibe 404.
- No hay WebSockets. El cliente hace polling de `GET /position` cada 2 a 5 segundos hasta ver `HABILITADO` (con token) o `CERRADA`. Las tandas reemplazan una habilitación uno a uno: cada `intervaloSegundos` salen los primeros `batchSize` clientes, en orden de llegada.
- Un scheduler revisa las filas abiertas. Cuando pasa `intervaloSegundos` desde la tanda anterior, habilita a los primeros `batchSize` clientes (FIFO) y les firma un JWT con `eventId`, `sub`, `jti`, `exp` y `type=QUEUE`. La misma persona no sale dos veces: sale del sorted set en la misma operación Lua. Si el proceso corta después de sacarla y antes de firmar, el valor queda `PENDIENTE`: el próximo ciclo o `GET /position` firma el token, una sola vez. Los tokens vencidos se borran en ese mismo ciclo.
- `POST /internal/events/{eventoId}/close` cierra la fila con motivo `CANCELADO`, `FINALIZADO` o `AGOTADO`. No entran clientes nuevos y no se emiten tokens. Quien ya estaba consulta `CERRADA`, sin token. Redis guarda el estado con AOF (`appendonly yes` en el compose). Al cerrar, las claves del evento reciben un TTL (`QUEUE_RETENTION`, 7 días por defecto) para no crecer sin límite; al reabrir por `AGOTADO` se les quita. Si Redis pierde los datos, el ingreso responde 409 `QUEUE_NOT_OPEN` y no se emiten tokens.
- Un JWT que el cliente ya recibió sigue siendo válido hasta su `exp` (5 minutos por defecto). No es de un solo uso: se puede reutilizar hasta que vence. No hay índice único sobre `queue_jti`. Inventory lo valida en local, sin llamar a queue. La reserva de un evento cancelado, finalizado o agotado se rechaza por el estado del evento en la tarea 5.1.
- La estimación es `(adelante / batchSize + 1) * intervaloSegundos`: quien está en la próxima tanda espera un intervalo.
- Claves Redis: `queue:{eventoId}:waiting` (sorted set), `queue:{eventoId}:state` (hash) y `queue:{eventoId}:seq`.
- Swagger UI: <http://localhost:8086/swagger-ui.html> — OpenAPI: <http://localhost:8086/v3/api-docs>

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8086` | Puerto HTTP |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `REDIS_TIMEOUT` | `2s` | Timeout del cliente |
| `QUEUE_BATCH_SIZE` | `50` | Clientes por tanda, usado para la estimación |
| `QUEUE_INTERVAL_SECONDS` | `30` | Segundos entre tandas, usado para la estimación |
| `INTERNAL_TOKEN` | `ticketya-internal-dev` | Token de `POST /internal/events/{eventoId}` |
| `QUEUE_TOKEN_SECRET` | `ticketya-queue-dev-secret-key-32b` | Firma HS256 del token de fila. Mínimo 32 bytes |
| `QUEUE_TOKEN_TTL` | `5m` | Vida del token de fila |
| `QUEUE_SCHEDULER_MS` | `5000` | Cada cuánto el scheduler revisa las filas abiertas |
| `QUEUE_SCHEDULER_ENABLED` | `true` | Apagar el scheduler en tests |
| `QUEUE_RETENTION` | `7d` | TTL de las claves de un evento al cerrar la fila |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d redis
cd queue-service
mvn test
mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8086/actuator/health
```

La respuesta debe incluir `"status":"UP"` y el componente `redis` en `UP`.

### Tests

`QueueServiceApplicationTests` verifica el health con Redis. `FilaRedisTest` cubre el ingreso y la posición. `TandaTokenTest` cubre las tandas y el vencimiento del token. `CierreFilaTest` cubre el cierre, la tanda que ya no sale y el reinicio de Redis con AOF. Se omiten si Docker no está corriendo.

### Flujo completo

Los defaults de tanda son 50 clientes y 30 segundos. Para recorrerlo en unos segundos, levantar el servicio con una tanda corta:

```bash
cd queue-service
QUEUE_BATCH_SIZE=1 QUEUE_INTERVAL_SECONDS=2 QUEUE_SCHEDULER_MS=1000 mvn spring-boot:run
```

En otra terminal, contra `http://localhost:8086`:

```bash
EVENTO=11111111-1111-4111-8111-111111111111
CLIENTE=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa

curl -s -X POST http://localhost:8086/internal/events/$EVENTO \
  -H "X-Internal-Token: ticketya-internal-dev"

curl -s -D - -X POST http://localhost:8086/api/queue/events/$EVENTO/join \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"

curl -s http://localhost:8086/api/queue/events/$EVENTO/position \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"

sleep 4

curl -s http://localhost:8086/api/queue/events/$EVENTO/position \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"

curl -s -X POST http://localhost:8086/internal/events/$EVENTO/close \
  -H "X-Internal-Token: ticketya-internal-dev" \
  -H "Content-Type: application/json" \
  -d '{"motivo":"AGOTADO"}'
```

El primer `join` responde 201 con `EN_ESPERA`. Repetirlo responde 200 y la misma posición. El `GET` de inmediato sigue en `EN_ESPERA`. Después del intervalo, el mismo `GET` responde `HABILITADO` y trae `token`. El cierre deja la consulta en `CERRADA`, sin token, y un ingreso nuevo responde 409 `QUEUE_CLOSED`.

La carpeta **Fila** de `postman/TicketYa.postman_collection.json` recorre los mismos pasos. Usa `queueUrl` (`http://localhost:8086`) porque abrir y cerrar no pasan por el gateway. Hay que levantar queue con `QUEUE_INTERVAL_SECONDS=2` y `QUEUE_SCHEDULER_MS=1000`: el request que espera el token se demora 4 segundos.
