# P3 — Orders (Caro)

**Servicio:** `orders-service` (8083, `orders_db` en 5435).
**Tareas OpenSpec:** 2.6, 6.1–6.7, 9.3, 10.2, 10.5.
**Día 1:** arrancás con **6.1** (migración V5) y **6.2** (saga de compra).

## 1. Antes de empezar

1. Leer [`../../plan-entrega-2.md`](../../plan-entrega-2.md) y tu sección del checklist.
2. Leer `openspec/changes/entrega-2-completo/specs/control-acceso-qr/spec.md`,
   `reportes-organizador/spec.md`, `consistencia-saga/spec.md`,
   `reserva-y-compra/spec.md`, `devoluciones-cancelacion/spec.md` y
   `observabilidad/spec.md`.
3. Leer `design.md`, decisiones 5, 7, 8, 9 y 10.
4. Levantar la base y el servicio:

```bash
docker compose -f docker-compose.dev.yml up -d orders-db
cd orders-service
mvn test
mvn spring-boot:run
curl http://localhost:8083/actuator/health
```

Swagger: <http://localhost:8083/swagger-ui.html>

## 2. Alcance

Sos dueño de `orders-service/`:

- Migración V5: email, organizador, snapshot de sector, `used_at/used_by`, saga, outbox,
  auditoría e índices de reporte.
- Saga de compra con estados, comandos idempotentes y compensaciones.
- Outbox y eventos `orden.pagada`, `orden.reintegrada`, `devolucion.aprobada`.
- Acceso QR con `ACCESS_STAFF`/`ADMIN` y actualización condicional atómica.
- Reportes por evento y sector.
- Resiliencia de pasarela e inventory.
- OpenAPI, README y contrato.

**No toques** catalog, inventory, queue, notification, recommendation ni gateway. Si necesitás
un cambio de contrato, avisá a P1/P2/P4/P5 y actualizá `docs/api-contract.md` en el mismo PR.

## 3. Orden de trabajo

| Orden | Tarea | Qué hacés | Verificación |
|---|---|---|---|
| 1 | 6.1 | Migración V5 y entidades | Aplica sobre base de Entrega 1 sin pérdida |
| 2 | 6.2 | Saga de compra con estados, pasos idempotentes y compensaciones | Aprobado, rechazado, timeout, fallo de inventory y de emisión; nunca `PAGADA` sin entradas |
| 3 | 6.3 | Outbox y eventos de compra/devolución | Fallo de RabbitMQ no pierde; reintento no duplica |
| 4 | 6.4 | `POST /api/orders/access/validate` con update condicional, 404/409 y auditoría | Dos escaneos concurrentes: 1×200 y 1×409 |
| 5 | 6.5 | Reporte por evento/sector con ventas, devoluciones, ocupación y recaudación | 200, 403, 404 y cálculo con devoluciones |
| 6 | 6.6 | Timeouts, reintentos, circuit breaker y fallback de pagos/inventory | WireMock lento, 5xx, circuito abierto y recuperación |
| 7 | 6.7 | OpenAPI/README/contrato de orders | Swagger, ejemplos y matriz de roles |
| 8 | 2.6 | Seguridad JWT/roles de orders | 401/403/200; cliente no accede a reportes ni entradas ajenas |
| 9 | 9.3 | `audit_log` de compra, devolución, cancelación e ingreso | Bitácora y log con la misma correlación |
| 10 | 10.2 | Postman v2 completo | Newman 100 % verde por gateway |
| 11 | 10.5 | Diagramas de arquitectura actualizados | Mermaid renderiza y coincide con `design.md` |

## 4. Contratos que tenés que respetar

- **Sobre de eventos:** `eventId`, `type`, `occurredAt`, `correlationId`, `version`, `payload`
  (`docs/eventos.md`, tarea 1.5). P1 (notification) y P5 (recommendation) consumen tus eventos.
- **Headers:** `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Correlation-Id`; endpoints
  internos con `X-Internal-Token`.
- **Access QR:** el rol `ACCESS_STAFF` lo emite el gateway (P4); la entrada es tuya. La
  actualización condicional es la garantía de uso único.
- **Reportes:** la capacidad y los precios vienen de catalog (P1) por endpoint interno; si
  catalog no responde, usá cache/fallback y marcalo.
- **Email:** lo propaga el gateway desde el JWT; guardalo en la orden y en `orden.pagada`.

## 5. Ramas sugeridas

| Rama | Tareas |
|---|---|
| `feat/orders-migracion-v5` | 6.1 |
| `feat/orders-saga` | 6.2 |
| `feat/orders-outbox` | 6.3 |
| `feat/orders-acceso-qr` | 6.4 |
| `feat/orders-reportes` | 6.5 |
| `feat/orders-resiliencia` | 6.6 |
| `docs/orders-openapi` | 6.7 |
| `feat/orders-seguridad` | 2.6 |
| `feat/orders-auditoria` | 9.3 |
| `test/postman-v2` | 10.2 |
| `docs/arquitectura-entrega-2` | 10.5 |

## 6. Criterio de terminado

- [ ] Tareas marcadas en `tasks.md`.
- [ ] `mvn test` verde en orders, incluida la suite de idempotencia y la de no doble venta.
- [ ] La orden nunca queda `PAGADA` sin entradas válidas.
- [ ] OpenAPI, README y contrato actualizados.
- [ ] Evidencia de la verificación en el PR.
- [ ] CI verde y PR aprobado por otro integrante.
- [ ] Sin secretos, `target/`, `.env` ni datos hardcodeados fuera del seed.
