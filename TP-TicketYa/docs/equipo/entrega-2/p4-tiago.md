# P4 — Gateway e infraestructura (Tiago)

**Componentes:** `api-gateway`, Docker, CI, observabilidad y compose integral.
**Tareas OpenSpec:** 1.3–1.5, 2.1–2.3, 2.8, 9.1, 9.4–9.7, 10.8.
**Día 1:** arrancás con **1.3** (contrato v2), **1.4** (Redis/Mongo/Rabbit) y **1.5** (sobre de eventos).

## 1. Antes de empezar

1. Leer [`../../plan-entrega-2.md`](../../plan-entrega-2.md) y tu sección del checklist.
2. Leer `openspec/changes/entrega-2-completo/specs/seguridad-jwt/spec.md`,
   `observabilidad/spec.md`, `despliegue-contenedores/spec.md` y
   `resiliencia-servicios/spec.md`.
3. Leer `design.md`, decisiones 2, 8, 10 y 12.
4. Estado actual del gateway:

```bash
cd api-gateway
mvn test
mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

## 2. Alcance

Sos dueño de:

- `api-gateway/`: login, JWT, matriz de roles, headers, correlación, rutas nuevas.
- `docker-compose.dev.yml` y `docker-compose.yml`: Redis, MongoDB, RabbitMQ, healthchecks,
  redes y volúmenes.
- Dockerfiles de todos los servicios (con revisión del dueño de cada uno).
- CI de GitHub Actions.
- Loki/Promtail/Grafana y logging JSON.
- Contrato v2 y sobre de eventos.

**No implementes features de negocio** de catalog, inventory, orders, queue, recommendation ni
notification. Coordinás con cada dueño el logging, el Dockerfile y los headers.

## 3. Orden de trabajo

| Orden | Tarea | Qué hacés | Verificación |
|---|---|---|---|
| 1 | 1.3 | Contrato v2, roles, headers, errores y `bearerAuth` | Revisión cruzada; ningún endpoint sin rol |
| 2 | 1.4 | Redis, MongoDB y RabbitMQ en el compose de desarrollo | `docker compose config` y health |
| 3 | 1.5 | Sobre de eventos, routing keys, colas y DLQ en `docs/eventos.md` | Mensaje de ejemplo y revisión de consumidores |
| 4 | 2.1 | `auth_db`, tabla `usuario`, BCrypt, usuarios de prueba y login | 200, 401 y 400 |
| 5 | 2.2 | Filtro JWT + matriz de roles + headers derivados | 401, 403, 200 y headers falsificados descartados |
| 6 | 2.3 | `X-Internal-Token`, bloqueo de `/internal/**` y propagación | 401 sin token; gateway no expone internos |
| 7 | 9.1 | Logging JSON + MDC + correlación en los siete servicios | Misma correlación en gateway y servicio |
| 8 | 9.4 | Loki + Promtail + Grafana, datasource y retención | Consulta real por correlación |
| 9 | 9.5 | Dockerfile multi-stage por servicio, no root y healthcheck | `docker build` de los siete |
| 10 | 9.6 | `docker-compose.yml` integral | `up --build`, healthy y smoke |
| 11 | 9.7 | CI: siete builds, compose, integración y OpenAPI | Workflow verde en un PR |
| 12 | 2.8 | Matriz endpoint × rol y carpeta Postman de seguridad | Evidencia 401/403/200 por fila |
| 13 | 10.8 | Clone limpio, compose, smoke, tag `entrega-2` | Tag sobre el commit entregado |

## 4. Contratos que tenés que respetar

- **Identidad:** el gateway emite y valida el JWT; los servicios leen `X-User-Id`,
  `X-User-Role`, `X-User-Email` y `X-Correlation-Id`. No cambies los nombres.
- **Roles:** `CLIENT`, `ORGANIZER`, `ADMIN`, `ACCESS_STAFF`. La matriz es default-deny.
- **Token de fila:** es un JWT separado firmado con `QUEUE_TOKEN_SECRET`; lo emite P2 y lo
  valida inventory. No lo emitas desde el gateway.
- **Eventos:** el sobre lo define tu tarea 1.5; P1, P3 y P5 lo consumen. Cualquier cambio se
  acuerda antes.
- **Compose:** los servicios usan nombres de host (`catalog-db`, `redis`, `rabbitmq`, etc.);
  no publiques puertos internos salvo en un override de desarrollo.

## 5. Ramas sugeridas

| Rama | Tareas |
|---|---|
| `docs/contrato-v2` | 1.3 |
| `chore/infra-redis-mongo-rabbit` | 1.4 |
| `docs/eventos` | 1.5 |
| `feat/gateway-auth` | 2.1, 2.2 |
| `feat/gateway-internal-token` | 2.3 |
| `feat/observabilidad-logging` | 9.1 |
| `chore/observabilidad-loki` | 9.4 |
| `chore/dockerfiles` | 9.5 |
| `chore/compose-integral` | 9.6 |
| `chore/ci-entrega-2` | 9.7 |
| `test/seguridad-matriz` | 2.8 |
| `chore/cierre-entrega-2` | 10.8 |

## 6. Criterio de terminado

- [ ] Tareas marcadas en `tasks.md`.
- [ ] Gateway con tests de login, JWT, roles y endpoints internos.
- [ ] `docker compose config` y `up --build` funcionando en un clone limpio.
- [ ] CI verde con los siete servicios.
- [ ] Loki consultable por correlación.
- [ ] Evidencia de la verificación en el PR.
- [ ] PR aprobado por otro integrante.
- [ ] Sin secretos, `target/`, `.env` ni credenciales reales.
