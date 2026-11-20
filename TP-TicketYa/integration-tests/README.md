# integration-tests

Pruebas end-to-end de TicketYa contra el **gateway** y los servicios reales. Es el módulo de
la tarea **10.1** (P5). No usa mocks de servicios: valida el flujo por el puerto 8080.

## Estado (Día 8 — 20/11/2026)

| Flujo | Clase / test | Estado |
|---|---|---|
| Gateway → salud y búsqueda pública de catalog | `GatewayCatalogIT` | Implementado y verde |
| Login → JWT, búsqueda y 401/403 | `TicketYaE2EIT.elLoginEmiteJwtYLaIdentidadEsObligatoriaEnLosEndpointsProtegidos` | Verde con `Authorization: Bearer` (2.1/2.2 mergeadas) |
| Fila → reserva de alta demanda | `TicketYaE2EIT.laFilaHabilitaLaReservaDeUnEventoDeAltaDemanda` | Verde |
| Compra → QR e idempotencia | `TicketYaE2EIT.laCompraGeneraUnaEntradaConQrYEsIdempotente` | Verde |
| Acceso QR con un solo uso + reporte | `TicketYaE2EIT.elPersonalDeAccesoValidaElQrUnaSolaVez` | Verde: doble escaneo (200/409) y totales del reporte |
| Compra → notificación y recomendaciones | `TicketYaE2EIT.laCompraAlimentaNotificacionesYRecomendaciones` | Verde |

Corrida del **20/11/2026** contra el compose integral: **8 tests, 0 fallos, 0 omitidos**.
Incluye el acceso QR y el reporte que antes se salteaban. La fila se levantó con
`QUEUE_BATCH_SIZE=1 QUEUE_INTERVAL_SECONDS=2 QUEUE_SCHEDULER_MS=500`; sin esos valores el
test de fila puede agotar el timeout. Evidencia:
[`../docs/evidencia/entrega-2-dia-8.txt`](../docs/evidencia/entrega-2-dia-8.txt).

Los tests se saltan si no se define `TICKETYA_E2E=true`, para que un `mvn test` sin entorno
no falle.

## Cómo correr la suite completa

### 1. Compose integral

Desde `TP-TicketYa`, con tandas cortas para no esperar 30 segundos:

```bash
QUEUE_BATCH_SIZE=1 QUEUE_INTERVAL_SECONDS=2 QUEUE_SCHEDULER_MS=500 \
  docker compose up -d --build
```

Esperar los healthchecks:

```bash
curl -fsS http://localhost:8080/actuator/health
for p in 8081 8082 8083 8084 8085 8086; do curl -fsS "http://localhost:$p/actuator/health"; done
```

`catalog-service` necesita `REDIS_HOST=redis` en el compose para que su health check de cache
quede `UP`; sin eso el gateway no arranca por el `depends_on: service_healthy`.

### 2. Suite

```bash
cd TP-TicketYa/integration-tests
TICKETYA_E2E=true mvn -B -ntp verify
```

Variables:

| Variable | Default | Uso |
|---|---|---|
| `TICKETYA_E2E` | `false` | `true` habilita los tests de integración |
| `TICKETYA_GATEWAY_URL` | `http://localhost:8080` | URL del gateway |
| `TICKETYA_E2E_TIMEOUT_MS` | `45000` | Espera máxima de la fila y de los consumidores asíncronos |

Resultado de la corrida del 19/11 con las siete piezas arriba:

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

El omitido es el acceso QR: la tarea 6.4 todavía no expone
`POST /api/orders/access/validate` y el rol `ACCESS_STAFF` no está en la matriz del gateway.

## Qué crea el escenario

`TicketYaE2EIT` arma un lugar, un sector numerado y un evento de alta demanda con fechas
futuras, publica el evento y reserva con el token de fila. Después compra con
`Idempotency-Key` y verifica que la notificación `orden.pagada` llegue por RabbitMQ y que
recommendation responda por el gateway. Todo se crea con nombres únicos por corrida; las
bases del compose se pueden reutilizar entre ejecuciones.

## Pendientes

1. Cuando P3 cierre 6.4, quitar la omisión del acceso QR y agregar `ACCESS_STAFF` al gateway.
2. Cuando orders exponga reportes (6.5), agregar el flujo de reporte por evento.
3. Llevar esta suite al workflow de CI con `docker compose` cuando 9.7 lo permita.
