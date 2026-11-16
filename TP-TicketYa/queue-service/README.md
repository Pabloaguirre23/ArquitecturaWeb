# queue-service

Fila virtual de TicketYa. Redis es el almacén primario.

- **Puerto:** 8086
- **Redis:** 7, puerto 6379, AOF activado
- **Estado:** ingreso, posición, tandas, token y cierre (tareas 4.1 a 4.3).

## Alcance actual

Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc y Spring Data Redis.

- `GET /actuator/health` reporta Redis (`redis`).
- `POST /internal/events/{eventoId}` abre la fila. Lo llama catalog al publicar un evento de alta demanda. Exige `X-Internal-Token`. Repetirlo no pisa el tamaño de tanda.
- `POST /api/queue/events/{eventoId}/join` ingresa al cliente. El primer ingreso responde 201. Repetirlo responde 200 y conserva la posición (`ZADD NX`). Sin fila responde 409 `QUEUE_NOT_OPEN`.
- `GET /api/queue/events/{eventoId}/position` devuelve posición (`ZRANK + 1`), personas adelante, estado `EN_ESPERA` y una estimación. Si una tanda ya lo habilitó, el estado es `HABILITADO` y la respuesta incluye el token. Quien no ingresó, o cuyo token venció, recibe 404.
- Un scheduler revisa las filas abiertas. Cuando pasa `intervaloSegundos` desde la tanda anterior, habilita a los primeros `batchSize` clientes (FIFO) y les firma un JWT con `eventId`, `sub`, `jti`, `exp` y `type=QUEUE`. La misma persona no sale dos veces: sale del sorted set en la misma operación Lua. Los tokens vencidos se borran en ese mismo ciclo.
- `POST /internal/events/{eventoId}/close` cierra la fila con motivo `CANCELADO`, `FINALIZADO` o `AGOTADO`. No entran clientes nuevos y no se emiten tokens. Quien ya estaba consulta `CERRADA`, sin token. Redis guarda el estado con AOF (`appendonly yes` en el compose). Si Redis pierde los datos, el ingreso responde 409 `QUEUE_NOT_OPEN` y no se emiten tokens.
- Un JWT que el cliente ya recibió sigue siendo válido hasta su `exp` (5 minutos por defecto). Inventory lo valida en local, sin llamar a queue. La reserva de un evento cancelado, finalizado o agotado se rechaza por el estado del evento en la tarea 5.1.
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

```bash
curl -X POST http://localhost:8086/internal/events/11111111-1111-4111-8111-111111111111 \
  -H "X-Internal-Token: ticketya-internal-dev"

curl -X POST http://localhost:8086/api/queue/events/11111111-1111-4111-8111-111111111111/join \
  -H "X-User-Id: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" \
  -H "X-User-Role: CLIENT"

curl -X POST http://localhost:8086/internal/events/11111111-1111-4111-8111-111111111111/close \
  -H "X-Internal-Token: ticketya-internal-dev" \
  -H "Content-Type: application/json" \
  -d "{\"motivo\":\"AGOTADO\"}"
```
