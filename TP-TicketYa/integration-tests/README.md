# integration-tests

Pruebas end-to-end de TicketYa contra el **gateway** y los servicios levantados con Maven
sobre las bases de `docker-compose.dev.yml`. Es el módulo de la tarea **10.1** (P5).

No usa mocks de servicios: el objetivo es validar el flujo real, por el puerto 8080.

## Estado (Día 5 — 17/11/2026)

| Flujo | Estado | Depende de |
|---|---|---|
| Gateway → búsqueda de catalog | **Primer flujo implementado** (`GatewayCatalogIT`) | gateway + catalog + catalog-db |
| Login → búsqueda | Parcial: la búsqueda ya está; el login con JWT todavía no existe | 2.1–2.3 (P4) |
| Fila → reserva | Pendiente de integrar los PRs de queue e inventory | 4.1–4.3, 5.1 (P2) |
| Compra → QR | Pendiente de la suite de orders/inventory | 6.x (P3) |
| Acceso QR | Pendiente del endpoint de validación de QR | 6.4 (P3) |
| Compra → recomendación/notificación | Pendiente del consumidor de notificaciones y del flujo completo | 7.3, 8.2 (P5/P1) |

El test se salta automáticamente si no se define `TICKETYA_E2E=true`, para que un
`mvn test` sin entorno no falle.

## Cómo correr el primer flujo

### 1. Base de catalog

```bash
cd TP-TicketYa
docker compose -f docker-compose.dev.yml up -d catalog-db
```

### 2. Catalog y gateway

En dos terminales (Java 21):

```bash
cd TP-TicketYa/catalog-service
mvn -B -ntp spring-boot:run
```

```bash
cd TP-TicketYa/api-gateway
mvn -B -ntp spring-boot:run
```

Esperar:

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/health
```

### 3. Suite de integración

```bash
cd TP-TicketYa/integration-tests
TICKETYA_E2E=true mvn -B -ntp verify
```

Variables:

| Variable | Default | Uso |
|---|---|---|
| `TICKETYA_E2E` | `false` | `true` habilita los tests de integración |
| `TICKETYA_GATEWAY_URL` | `http://localhost:8080` | URL del gateway |

Resultado esperado del primer flujo:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Próximos pasos

1. Cuando P4 cierre 2.1–2.3, agregar `LoginIT` y reemplazar los headers de identidad por JWT.
2. Cuando se integren los PRs de queue, agregar `QueueReservationIT`: abrir fila, ingresar,
   esperar tanda, reservar con `X-Queue-Token`.
3. Cuando orders/inventory estén integrados, agregar `PurchaseQrIT` y `AccessQrIT`.
4. Cuando notification/recommendation estén integrados, agregar `PurchaseEventsIT`:
   publicar `orden.pagada` y verificar historial + notificación.
5. Agregar estos tests al compose integral (9.6) y al CI (9.7) cuando existan.
