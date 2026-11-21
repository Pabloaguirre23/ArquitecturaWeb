# Brief para Caro — 6.4 acceso QR y 6.5 reportes

**Rama base:** `feat/entrega-2-dia-1` (commit `c4ba176`).
**Objetivo:** cerrar las dos features P0 que faltan del enunciado. **Prioridad: 6.4 primero**,
porque la suite E2E ya tiene el caso escrito y hoy lo salta.

No hace falta tocar el gateway ni las migraciones: ya están preparados. El foco es
`orders-service/`.

---

## 1. Lo que ya está listo (no lo rehacer)

- V5 (`orders-service/src/main/resources/db/migration/V5__saga_outbox_acceso_reportes.sql`):
  - `entrada.used_at`, `entrada.used_by`.
  - índices `idx_entrada_evento_sector_estado` y `idx_orden_evento_estado`.
  - tablas `audit_log`, `saga_compra`, `outbox_event`.
  - snapshot en `orden`: `evento_organizador_id`, `sector_nombre`, `evento_tipo`,
    `evento_artistas`, `evento_ciudad`, `cliente_email`.
- `AuditLog` y `AuditLogRepository` existen, pero **nadie los usa todavía** (tarea 9.3).
- Gateway:
  - `POST /api/orders/access/validate` ya está permitido para `ACCESS_STAFF`/`ADMIN`.
  - `GET /api/orders/reports/events/{eventoId}` ya está permitido para `ORGANIZER`/`ADMIN`
    (regla agregada en el PR #32). Si cambiás la ruta, avisá para ajustar la regla.
- Catalog expone los datos internos del reporte:
  - `GET /internal/events/{eventoId}/report`
  - requiere `X-Internal-Token`.
  - devuelve `eventoId`, `organizadorId`, `sectores[]` con `sectorId`, `nombre`, `tipo`,
    `capacidad`, `precio`.
- `TicketYaE2EIT` ya tiene el test `elPersonalDeAccesoValidaElQrUnaSolaVez`; hoy se salta con
  `Assumptions.assumeTrue` hasta que exista el endpoint. Cuando lo implementes, **quitá ese
  `assumeTrue`** y la suite lo va a ejecutar de verdad.

---

## 2. Tarea 6.4 — `POST /api/orders/access/validate`

**Ruta:** `POST /api/orders/access/validate`
**Roles:** `ACCESS_STAFF` y `ADMIN` (el gateway ya lo exige; en orders usá
`RequestIdentity` y `requireRole` como en el resto).

**Body:**
```json
{ "qrCode": "235e9e9e-3451-4605-94a1-ae9cd55aef18", "eventoId": "d0000000-0000-4000-8000-000000000001" }
```

**Comportamiento:**
1. Buscar `Entrada` por `qrCode`.
2. Si no existe → `404 TICKET_NOT_FOUND`.
3. Si es de otro evento → `409 TICKET_WRONG_EVENT` (no modificar).
4. Si `estado` no es `VALIDA` o `usedAt != null` → `409 TICKET_ALREADY_USED` /
   `409 TICKET_NOT_VALID` según el estado (`DEVUELTA`, `EN_REINTEGRO`, `UTILIZADA`).
5. Si es válida: **UPDATE condicional atómico**:
   ```sql
   UPDATE entrada
      SET estado = 'UTILIZADA', used_at = :ahora, used_by = :usuario
    WHERE id = :id AND evento_id = :eventoId AND estado = 'VALIDA' AND used_at IS NULL
   ```
   Si el update afecta 1 fila → `200`; si afecta 0 → volver a leer y distinguir 404/409.
   **No hagas un `find` + `save`**: dos escaneos concurrentes tienen que dar uno `200` y
   otro `409`.
6. Respuesta `200` sugerida:
   ```json
   {
     "entradaId": "...",
     "ordenId": "...",
     "eventoId": "...",
     "sectorId": "...",
     "estado": "UTILIZADA",
     "usedAt": "2026-11-20T21:00:00-03:00",
     "usedBy": "55555555-5555-4555-8555-555555555555"
   }
   ```
7. **Bitácora (9.3):** crear un `AuditService` que use `AuditLogRepository` y registre
   `INGRESO_VALIDADO` (éxito) o `INGRESO_RECHAZADO` (con el motivo) con `entradaId`,
   `usuarioId`, `rol`, `resultado` y `X-Correlation-Id`. Que sea best-effort: si la
   auditoría falla, no revierte el ingreso.

**Tests:**
- `AccessControllerTest` (MockMvc): 200, 404, 409 usado, 409 otro evento, 409 devuelta,
  403 CLIENT, 401 sin identidad.
- `AccessConcurrencyIT` (Testcontainers/PostgreSQL): dos hilos con `CountDownLatch` sobre el
  mismo QR → exactamente un 200 y un 409; la base tiene `used_at` una sola vez.
- Quitar el `assumeTrue` de `TicketYaE2EIT`.

---

## 3. Tarea 6.5 — `GET /api/orders/reports/events/{eventoId}`

**Roles:** `ORGANIZER` dueño del evento o `ADMIN`. `CLIENT` → 403.

**Respuesta sugerida:**
```json
{
  "eventoId": "...",
  "organizadorId": "...",
  "sectores": [
    {
      "sectorId": "...",
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

**Cálculo (seguí el escenario de la spec):**
- `vendidas` = entradas pagadas del evento (incluye las que después se devolvieron).
- `devueltas` = entradas en estado `DEVUELTA` (o devoluciones aprobadas).
- `netas` = `vendidas - devueltas`.
- `ocupacion` = `netas / capacidad` (0.35 = 35 %); si no hay capacidad, `null`.
- `recaudacion` = suma de los ítems de órdenes pagadas menos los reintegros aprobados.
- Zona `NO_NUMERADO`: la capacidad es la del sector y las ventas son las cantidades.

**Datos:**
- Ventas: `orden`, `orden_item`, `entrada`, `devolucion`, `pago` de `orders_db`.
- Capacidad/precio: `GET /internal/events/{eventoId}/report` de catalog con
  `X-Internal-Token`, timeout corto (1–2 s) y **cache en memoria con TTL** (60 s) para el
  fallback. Si catalog no responde:
  - usar `orden.evento_organizador_id`, `sector_nombre` y el precio del ítem;
  - devolver `datosDesactualizados: true`;
  - si no hay capacidad, `ocupacion: null`;
  - si no se puede verificar el dueño ni con el snapshot → `503`.
- Autorización: comparar `X-User-Id` con `organizadorId` de catalog; `ADMIN` pasa siempre.
- Evento inexistente en catalog → `404 EVENT_NOT_FOUND`.

**Tests:**
- `ReportControllerTest`: dueño 200, admin 200, otro organizador 403, cliente 403,
  evento inexistente 404, evento sin ventas con ceros.
- `ReportCalculationTest`: 100 capacidad, 40 pagadas, 5 devueltas → 40/5/35 y 35 %;
  recaudación con reintegro; zona no numerada.
- `ReportIntegrationTest` (Testcontainers): datos reales y devolución aprobada.
- E2E: después del escaneo, pedir el reporte con el token `ORGANIZER` y verificar totales.

---

## 4. Si queda tiempo (después de 6.4 y 6.5)

- **9.3 bitácora:** completar `AuditService` para compra, devolución, cancelación e ingreso.
  La tabla y el repositorio ya existen.
- **6.7 docs:** `orders-service/README.md`, `docs/api-contract.md` §5 (acceso y reportes),
  `docs/seguridad.md` (filas de access/report), `openapi/orders-openapi.json`.
- **2.6 roles:** verificar que orders rechaza `CLIENT` en reportes y permite `ACCESS_STAFF` en
  access. El gateway ya lo hace; sumá tests del servicio.
- **6.6 resiliencia** (si hay margen): timeouts/circuit breaker de la pasarela e inventory.

---

## 5. Cómo verificar y entregar

```bash
cd orders-service && mvn -B -ntp test
cd ../integration-tests && TICKETYA_E2E=true mvn -B -ntp verify
```

- Suite E2E esperada: **8 tests, 0 fallos, 0 omitidos** (hoy hay 1 omitido por 6.4).
- Evidencia en `docs/evidencia/entrega-2-dia-8-caro.txt` (tests, dos escaneos, reporte).
- Marcá en `openspec/changes/entrega-2-completo/tasks.md` 6.4, 6.5, 9.3 y 6.7 con la
  evidencia.
- PR contra `feat/entrega-2-dia-1`; no toques `main`.

**No hagas** un `find` + `save` para el escaneo, ni un `SELECT` + `UPDATE` en dos pasos: la
concurrencia es el punto central de 6.4.

Cualquier duda de la ruta del reporte o del gateway, avisame y lo ajustamos en el mismo PR.
