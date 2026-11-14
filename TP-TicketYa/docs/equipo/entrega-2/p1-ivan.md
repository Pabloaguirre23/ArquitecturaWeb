# P1 — Catalog + Notification (Iván)

**Servicios:** `catalog-service` (8081, `catalog_db` en 5433) y `notification-service` (nuevo, `notification_db`).
**Tareas OpenSpec:** 1.8, 2.4, 3.1–3.7, 8.1–8.4, 9.2.
**Día 1:** arrancás con **1.8** (esqueleto de notification-service) y **3.1** (migración de catálogo).

## 1. Antes de empezar

1. Leer [`../../plan-entrega-2.md`](../../plan-entrega-2.md) y tu sección del checklist.
2. Leer `openspec/changes/entrega-2-completo/specs/gestion-eventos-lugares/spec.md`,
   `notificaciones/spec.md`, `cache-catalogo/spec.md` y `observabilidad/spec.md`.
3. Leer `design.md`, decisiones 1, 3, 6, 9 y 10.
4. Levantar la base y el servicio:

```bash
docker compose -f docker-compose.dev.yml up -d catalog-db
cd catalog-service
mvn test
mvn spring-boot:run
curl http://localhost:8081/actuator/health
```

Swagger: <http://localhost:8081/swagger-ui.html>

## 2. Alcance

Sos dueño de `catalog-service/` y `notification-service/`:

- Catalog: alta demanda, edición de evento, límite configurable, outbox, cache, endpoints
  internos de reporte/recomendación, integración con queue y auditoría.
- Notification: tabla de notificaciones, sender mock, consumidores de RabbitMQ, reintentos,
  DLQ, consulta propia y notificaciones de artista favorito.
- OpenAPI, README y contrato de ambos servicios.

**No toques** `inventory-service/`, `orders-service/`, `queue-service/`, `recommendation-service/`
ni `api-gateway/`. Si necesitás un cambio de contrato, avisá a P2/P3/P4 y actualizá
`docs/api-contract.md` en el mismo PR.

## 3. Orden de trabajo

| Orden | Tarea | Qué hacés | Verificación |
|---|---|---|---|
| 1 | 1.8 | Esqueleto de notification-service con Spring Boot, AMQP, PostgreSQL, Actuator, Swagger y Dockerfile | `mvn package`, health y conexión a la base |
| 2 | 2.4 | Seguridad JWT/roles de catalog y notification | 401/403/200 en endpoints propios |
| 3 | 3.1 | Migración `alta_demanda`, límite configurable y `PUT /api/catalog/events/{id}` | Tests de edición, 403, 409 y 400 |
| 4 | 3.2 | `outbox_event` y publicador de eventos de catálogo | Evento en la misma transacción; fallo de RabbitMQ no pierde |
| 5 | 8.1 | Tabla `notificacion`, `NotificationSender` y sender mock | Envío exitoso y fallo con reintento |
| 6 | 3.3 | Cache Redis de lugares, detalle estático y búsqueda | Hit, miss, TTL y `X-Cache` |
| 7 | 3.4 | Invalidación de cache al publicar/editar/cancelar | Detalle y búsqueda se recalculan |
| 8 | 3.5 | Endpoints internos de reporte y candidatos de recomendación | 200 con token, 401 sin token |
| 9 | 8.2 | Consumidor de `orden.pagada` con idempotencia, reintentos y DLQ | Compra no se bloquea si falla; duplicado no duplica aviso |
| 10 | 3.6 | Integración catalog → queue al publicar/cancelar/agotar | Fila se abre/cierra; queue caído no bloquea evento normal |
| 11 | 8.3 | Consumidores de cancelación, cambio y artista favorito | Coincidencia, no coincidencia y sin compradores |
| 12 | 8.4 | `GET /api/notifications` y docs | Solo propias, 403 ajenas, OpenAPI |
| 13 | 9.2 | `audit_log` de catalog | Publicación, edición y cancelación registradas |
| 14 | 3.7 | OpenAPI, README y contrato de catalog | Swagger, ejemplos y códigos |

## 4. Contratos que tenés que respetar

- **Sobre de eventos:** `eventId`, `type`, `occurredAt`, `correlationId`, `version`, `payload`.
  Lo define P4 en `docs/eventos.md` (tarea 1.5). No inventes un formato distinto.
- **Headers:** el gateway propaga `X-User-Id`, `X-User-Role`, `X-User-Email` y
  `X-Correlation-Id`. Los endpoints internos exigen `X-Internal-Token`.
- **Queue:** P2 define el contrato de `queue-service` (tarea 4.4). Catalog solo lo llama para
  abrir/cerrar fila; no lee Redis.
- **Notification:** P4 define la topología de RabbitMQ; P3 publica `orden.pagada` y
  `orden.reintegrada`; P5 expone preferencias para artista favorito.

## 5. Ramas sugeridas

| Rama | Tareas |
|---|---|
| `feat/notification-esqueleto` | 1.8 |
| `feat/catalog-alta-demanda-edicion` | 3.1 |
| `feat/catalog-outbox` | 3.2 |
| `feat/notification-base` | 8.1 |
| `feat/catalog-cache` | 3.3, 3.4 |
| `feat/catalog-internal-reportes` | 3.5 |
| `feat/notification-compra` | 8.2 |
| `feat/catalog-queue` | 3.6 |
| `feat/notification-eventos` | 8.3 |
| `feat/notification-consulta` | 8.4 |
| `feat/catalog-auditoria` | 9.2 |
| `docs/catalog-openapi` | 3.7 |

## 6. Criterio de terminado

- [ ] Tareas marcadas en `tasks.md`.
- [ ] `mvn test` verde en catalog y notification.
- [ ] OpenAPI y README de ambos servicios actualizados.
- [ ] `docs/api-contract.md` actualizado si cambió un endpoint.
- [ ] Evidencia de la verificación en el PR.
- [ ] CI verde y PR aprobado por otro integrante.
- [ ] Sin secretos, `target/`, `.env` ni datos hardcodeados fuera del seed.
