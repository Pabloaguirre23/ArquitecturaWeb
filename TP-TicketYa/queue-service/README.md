# queue-service

Fila virtual de TicketYa. Redis es el almacén primario.

- **Puerto:** 8086
- **Redis:** 7, puerto 6379, AOF activado
- **Estado:** esqueleto (tarea 1.6). El ingreso, la posición, las tandas y el token llegan en 4.1–4.4.

## Alcance actual

Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc y Spring Data Redis.

- `GET /actuator/health` reporta Redis (`redis`).
- Swagger UI: <http://localhost:8086/swagger-ui.html> — OpenAPI: <http://localhost:8086/v3/api-docs>

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8086` | Puerto HTTP |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `REDIS_TIMEOUT` | `2s` | Timeout del cliente |
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

`QueueServiceApplicationTests` (Testcontainers Redis 7) arranca el contexto, hace `PING` y verifica que `/actuator/health` reporte Redis conectado. Se omite si Docker no está corriendo.
