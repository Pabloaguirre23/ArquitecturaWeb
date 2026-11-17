# P2 — Inventory + Queue (Pablo)

**Servicios:** `inventory-service` (8082, `inventory_db` en 5434) y `queue-service` (Redis, 8086).
**Hechas:** 1.6, 4.1, 4.2, 4.3, 4.4, 5.1, 5.2.
**Quedan:** 5.3, 5.4, 5.5, 2.5, 10.3.

## 1. Antes de empezar

1. Leer [`../../plan-entrega-2.md`](../../plan-entrega-2.md) y tu sección del checklist.
2. Leer `openspec/changes/entrega-2-completo/specs/fila-virtual/spec.md`,
   `reserva-y-compra/spec.md`, `resiliencia-servicios/spec.md` y
   `observabilidad/spec.md`.
3. Leer `design.md`, decisiones 4, 6, 8 y 12.
4. Levantar la base y el servicio:

```bash
docker compose -f docker-compose.dev.yml up -d inventory-db
cd inventory-service
mvn test
mvn spring-boot:run
curl http://localhost:8082/actuator/health
```

Swagger: <http://localhost:8082/swagger-ui.html>

## 2. Alcance

Sos dueño de `inventory-service/` y `queue-service/`:

- Queue: modelo Redis, ingreso, posición, tandas, token firmado, cierre y recuperación.
- Inventory: validación del token de fila, cancelación de reserva, resiliencia y reconciliación.
- OpenAPI, README y contrato de ambos servicios.

**No toques** catalog, orders, notification, recommendation ni gateway. Si necesitás un cambio
de contrato, avisá a P1/P3/P4 y actualizá `docs/api-contract.md` en el mismo PR.

## 3. Orden de trabajo

| Orden | Tarea | Qué hacés | Verificación |
|---|---|---|---|
| 1 | 5.3 | Timeouts, reintentos y circuit breaker hacia catalog | WireMock 5xx, timeout y recuperación |
| 2 | 5.4 | Reconciliación de disponibilidad y no doble venta con fila | 100 hilos sigue dando 1 éxito; liberación no duplica cupo |
| 3 | 5.5 | OpenAPI/README de inventory | Swagger y ejemplos 403/409/410 |
| 4 | 2.5 | Seguridad JWT/roles de inventory y queue | 401/403/200 por endpoint |
| 5 | 10.3 | Plan JMeter del flujo de compra y corridas de 500/1000 usuarios | Resultados crudos y comando reproducible |

## 4. Contratos que tenés que respetar

- **Token de fila:** JWT firmado con `QUEUE_TOKEN_SECRET`, claims `eventId`, `sub`, `jti`,
  `exp`, `type=QUEUE`. Inventory lo valida localmente; no llama a queue en el camino crítico.
- **Headers:** `X-User-Id`, `X-User-Role`, `X-Correlation-Id`; endpoints internos con
  `X-Internal-Token`.
- **Queue API:** `join`, `position` y cierre. P1 (catalog) llama a queue al publicar/cancelar;
  P3 (orders) no necesita conocer Redis.
- **No doble venta:** el mecanismo de la Entrega 1 (locks/updates condicionales) no se toca; el
  token es una condición adicional, no un reemplazo.

## 5. Ramas sugeridas

| Rama | Tareas |
|---|---|
| `feat/inventory-resiliencia` | 5.3 |
| `feat/inventory-reconciliacion` | 5.4 |
| `docs/inventory-contrato` | 5.5 |
| `feat/queue-inventory-seguridad` | 2.5 |
| `test/carga-jmeter` | 10.3 |

## 6. Criterio de terminado

- [ ] Tareas marcadas en `tasks.md`.
- [ ] `mvn test` verde en inventory y queue.
- [ ] La prueba de no doble venta de 100 hilos sigue verde.
- [ ] OpenAPI y README de ambos servicios actualizados.
- [ ] Evidencia de la verificación en el PR.
- [ ] CI verde y PR aprobado por otro integrante.
- [ ] Sin secretos, `target/`, `.env` ni datos hardcodeados fuera del seed.
