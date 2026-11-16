# queue-service

Fila virtual de TicketYa. Redis es el almacén primario.

- **Puerto:** 8086
- **Redis:** 7, puerto 6379, AOF activado
- **Estado:** modelo Redis de la fila (tarea 4.1). Las tandas y el token llegan en 4.2.

## Alcance actual

Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc y Spring Data Redis.

- `GET /actuator/health` reporta Redis (`redis`).
- `POST /internal/events/{eventoId}` abre la fila. Lo llama catalog al publicar un evento de alta demanda. Exige `X-Internal-Token`. Repetirlo no pisa el tamaño de tanda.
- `POST /api/queue/events/{eventoId}/join` ingresa al cliente. El primer ingreso responde 201. Repetirlo responde 200 y conserva la posición (`ZADD NX`). Sin fila responde 409 `QUEUE_NOT_OPEN`.
- `GET /api/queue/events/{eventoId}/position` devuelve posición (`ZRANK + 1`), personas adelante, estado `EN_ESPERA` y una estimación. Quien no ingresó recibe 404.
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
| `QUEUE_TOKEN_SECRET` | `ticketya-queue-dev` | Firma del token de fila. Solo para desarrollo |
| `QUEUE_TOKEN_TTL` | `5m` | Vida del token de fila |

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

`QueueServiceApplicationTests` verifica el health con Redis. `FilaRedisTest` cubre el primer ingreso, el reingreso sin duplicar, la posición y el 409 de un evento sin fila. Se omiten si Docker no está corriendo.

```bash
curl -X POST http://localhost:8086/internal/events/11111111-1111-4111-8111-111111111111 \
  -H "X-Internal-Token: ticketya-internal-dev"

curl -X POST http://localhost:8086/api/queue/events/11111111-1111-4111-8111-111111111111/join \
  -H "X-User-Id: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" \
  -H "X-User-Role: CLIENT"
```
