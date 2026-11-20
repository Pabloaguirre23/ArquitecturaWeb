# Informe de prueba de carga — Entrega 2

Prueba del flujo de compra de TicketYa por el gateway: login JWT → búsqueda → reserva →
pago. El objetivo es medir la capacidad del compose integral, encontrar el primer cuello de
botella y dejar los resultados crudos y el comando reproducible.

- **Fecha:** 19/11/2026 (Día 7).
- **Tarea:** 10.4 (P5), con el plan de la tarea 10.3 (P2).
- **Estado:** 500 usuarios sin errores; 1000 usuarios con 5,53 % de error y un cuello de
  botella identificado en el detalle de catalog.

## 1. Resumen ejecutivo

| Usuarios | Muestras | Errores | Throughput | Compras OK/s | p95 pago |
|---|---|---|---|---|---|
| 500 | 2000 | 0 (0,00 %) | 33,5 muestras/s | 8,4/s | 122 ms |
| 1000 | 4000 | 221 (5,53 %) | 52,3 muestras/s | 10,7/s | 7,2 s |

- Con **500 usuarios** el flujo completo responde en decenas o cientos de milisegundos y no
  pierde ninguna operación.
- Con **1000 usuarios** el sistema sostiene la búsqueda y la reserva, pero el pago empieza a
  fallar (82 % de pagos OK). La causa no es la base de orders: es la llamada síncrona de
  orders a `GET /api/catalog/events/{id}` para enriquecer el snapshot, que no acierta la
  caché porque el detalle incluye disponibilidad dinámica.
- Antes de las correcciones de hoy, el mismo plan daba **0 % de pagos exitosos** en 500 y
  1000 usuarios: el pool de orders (10 conexiones) se agotaba con la transacción externa que
  abarcaba los pasos HTTP.

> **Alcance:** la corrida usa un solo cliente demo (`cliente@ticketya.test`) repetido en todos
> los hilos; no mide alta de usuarios distintos ni login con credenciales únicas. La
> recomendación de caché/snapshot de §6.3 es la que explica el 5,53 % de error a 1000 usuarios.

## 2. Entorno

| Componente | Detalle |
|---|---|
| Host | macOS 27.2.1, Apple Silicon (A18 Pro), 8 GB de RAM |
| Docker Desktop | VM con 6 vCPU y 4 GB de RAM |
| Java | Temurin 21.0.12.1 |
| Maven | 3.9.16 |
| JMeter | 5.6.3 nativo (no contenedor) |
| Servicios | `docker compose -f docker-compose.yml up -d --build` |
| Gateway | `http://localhost:8080` (único puerto publicado) |

El compose levanta 13 contenedores: 7 servicios, 4 PostgreSQL, MongoDB, RabbitMQ y Redis.
Todos quedaron `healthy` antes de cada corrida.

> Los números son una **cota inferior**: la VM de Docker tiene 4 GB y comparte CPU con
> JMeter. Una máquina con más recursos debería mejorar los percentiles, sobre todo el login
> con BCrypt.

## 3. Datos y plan

- **Plan:** [`docs/evidencia/carga/compra.jmx`](evidencia/carga/compra.jmx) (plan de P2, PR #26; copia para la evidencia). Cuatro samplers por
  hilo: `01 login`, `02 buscar`, `03 reservar`, `04 pagar`. Ramp-up de 60 s, una iteración
  por hilo.
- **Usuarios:** `usuarios.csv` con el cliente demo `cliente@ticketya.test` / `demo`. El
  gateway mantiene cuatro usuarios demo en memoria; para la carga se repite el cliente
  (el límite por cliente del evento se configuró en 1000).
- **Evento:** se crea uno nuevo por corrida con
  [`docs/evidencia/carga/setup-evento.py`](evidencia/carga/setup-evento.py): lugar, sector
  `NO_NUMERADO` de capacidad 2000, evento `RECITAL` sin alta demanda, `limitePorCliente`
  1000, precio y publicación. Así se evita que el cupo del seed (100) distorsione el
  resultado con 409 esperados.
- **Comando:**

```bash
cd docs/evidencia/carga
HEAP="-Xms1g -Xmx2g" jmeter -n -t compra.jmx \
  -Jusers=500 -Jramp=60 -Jhost=localhost -Jport=8080 \
  -Jusuarios=usuarios.csv \
  -JeventoId=<evento> -JsectorId=<sector> \
  -l resultados/compra-500-final.jtl
```

Para 1000 se repite con `-Jusers=1000`, un evento nuevo y la salida
`compra-1000-final.jtl`.

## 4. Resultados finales

### 4.1 500 usuarios

2000 muestras en 59,7 s; **0 errores**.

| Sampler | n | OK | p50 | p95 | p99 | máx |
|---|---|---|---|---|---|---|
| 01 login | 500 | 100 % | 73 ms | 178 ms | 356 ms | 563 ms |
| 02 buscar | 500 | 100 % | 5 ms | 31 ms | 88 ms | 185 ms |
| 03 reservar | 500 | 100 % | 6 ms | 32 ms | 85 ms | 120 ms |
| 04 pagar | 500 | 100 % | 24 ms | 122 ms | 273 ms | 411 ms |

### 4.2 1000 usuarios

4000 muestras en 76,5 s; **3779 OK y 221 errores (5,53 %)**.

| Sampler | n | OK | Errores | p50 | p95 | p99 | máx |
|---|---|---|---|---|---|---|---|
| 01 login | 1000 | 99,8 % | 2 timeouts | 6,8 s | 12,2 s | 13,6 s | 15,0 s |
| 02 buscar | 1000 | 100 % | 0 | 2,5 s | 5,8 s | 7,6 s | 9,5 s |
| 03 reservar | 1000 | 96,1 % | 37 × 500, 2 × 401 | 2,8 s | 6,1 s | 8,1 s | 10,0 s |
| 04 pagar | 1000 | 82,0 % | 159 × 502, 18 × 404, 1 timeout, 2 × 401 | 3,9 s | 7,2 s | 11,4 s | 15,0 s |

El login con 1000 hilos es el sampler más caro (p50 6,8 s) porque el gateway valida BCrypt
en CPU; no es el límite del flujo de compra, pero explica parte de la latencia inicial.

## 5. Antes y después de las correcciones

| Corrida | Configuración | Errores | Pagos OK | Observación |
|---|---|---|---|---|
| 500 baseline | Pool orders 10 + transacción externa | 610/2000 (30,5 %) | 0/500 | Hikari `waiting≈200`, 30 s; login 404 porque 2.1 no estaba |
| 1000 baseline | Pool orders 10 + transacción externa | 1000/4000 (25,0 %) | 0/1000 | Mismo cuello; los 409 del seed también inflaban |
| 500 pool | Pool orders 40 + transacción externa | 0/2000 | 500/500 | El pool resuelve la cola, pero la transacción sigue tomando una conexión durante los HTTP |
| 1000 pool | Pool orders 40 + transacción externa | 75/4000 (1,88 %) | 925/1000 | La transacción externa limita la concurrencia y protege a los servicios de abajo |
| 1000 tx | Sin transacción externa, inventory pool 20 | 639/4000 (15,97 %) | 575/1000 | Al liberar concurrencia, inventory (pool 20) pasa a ser el cuello |
| **500 final** | Sin transacción externa, pools 40 | **0/2000** | **500/500** | Corrida sobre stack ya calentado |
| **1000 final** | Sin transacción externa, pools 40 | **221/4000 (5,53 %)** | **820/1000** | Catalog se vuelve el cuello por el detalle sin caché |

Las corridas crudas están en [`docs/evidencia/carga/`](evidencia/carga/):

- `compra-500-baseline.jtl`, `compra-1000-baseline.jtl` (PR #26, antes de las correcciones).
- `compra-500-pool.jtl`, `compra-1000-pool.jtl` (solo pool).
- `compra-1000-tx.jtl` (transacción corta con inventory pool 20).
- `compra-500-final.jtl`, `compra-1000-final.jtl` (configuración entregada).
- `jmeter-500-final.log`, `jmeter-1000-final.log`.

## 6. Análisis de cuello de botella

### 6.1 Orders: pool y transacción larga (corregido)

La primera corrida mostró el problema más grave:

```text
HikariPool-1 - Connection is not available, request timed out after 30006ms
(total=10, active=10, idle=0, waiting=193)
```

`OrderService.crear` estaba anotado `@Transactional` y dentro de esa transacción llamaba a
inventory y catalog. Con 10 conexiones, 200 pedidos esperaban 30 s y el pago terminaba en
timeout. Se corrigió:

- `OrderService.crear` ya no abre una transacción que abarque los pasos HTTP; cada paso
  persiste en su propia transacción corta (`REQUIRES_NEW`).
- `IdempotencyService.completar` pasó de `MANDATORY` a `REQUIRES_NEW`.
- Pool de orders a 40 conexiones y timeout de 5 s.

Con eso, 500 usuarios quedaron en 0 errores y el pago bajó de 15 s a 122 ms (p95).

### 6.2 Inventory: pool y locks (mitigado)

Al liberar la concurrencia de orders, inventory pasó a ser el límite:

```text
HikariPool-1 - Connection is not available, request timed out after 3000ms
(total=20, active=20, idle=0, waiting=200)
```

Se subió el pool a 40 y el timeout a 5 s (`DB_POOL_SIZE`, `DB_CONNECTION_TIMEOUT_MS`).
Los errores de 1000 usuarios bajaron de 15,97 % a 5,53 %. El `lock_timeout=3000` de
PostgreSQL sigue acotando las esperas por asiento/zona.

### 6.3 Catalog: detalle sin caché (pendiente)

Con 1000 usuarios, el error dominante es el pago 502:

```text
No se pudo enriquecer el snapshot del evento ... desde catalog:
I/O error on GET request ... Read timed out
```

`orders` llama a `GET /api/catalog/events/{id}` por cada compra para completar
`eventoTipo`, `artistas`, `ciudad` y `organizadorId` del snapshot. Ese endpoint devuelve
`X-Cache: MISS` en llamadas repetidas porque el detalle incluye **disponibilidad dinámica**
(consulta a inventory) y por diseño no se cachea entero. A 1000 usuarios, catalog atiende
1000 detalles y 1000 callbacks de disponibilidad; el read timeout de 1 s de orders corta la
compra.

Recomendaciones (no bloquean la entrega, pero explican el 5,53 %):

1. Exponer un `GET /internal/events/{id}/snapshot` con los datos estáticos (tipo, artistas,
   ciudad, organizador, sector/precio) cacheado en Redis, sin disponibilidad.
2. O mover el enriquecimiento a un paso asíncrono/outbox: la orden ya tiene la reserva de
   inventory; el snapshot se completa después y el evento `orden.pagada` sale con los datos
   disponibles.
3. Cachear el detalle por evento en orders con TTL corto para no repetir la llamada 1000
   veces sobre el mismo evento.

### 6.4 Login BCrypt

Con 500 usuarios el login tiene p50 73 ms; con 1000 sube a 6,8 s porque el gateway calcula
BCrypt en CPU para cada intento. Es esperable y no afecta la corrección; para una prueba de
carga de 1000 usuarios conviene un usuario de carga con hash precalculado o un rate limit
explícito.

## 7. Conclusiones

- El flujo de compra soporta **500 usuarios concurrentes sin errores** en la VM de 6 vCPU/4
  GB, con p95 de pago de 122 ms.
- A **1000 usuarios** el sistema no pierde datos, pero el 5,53 % de las operaciones falla por
  timeouts en cascada desde catalog; no es un problema de la base de orders ni de inventory
  después de los pools.
- Las dos correcciones de infraestructura (transacción corta + pools) son necesarias y ya
  están en la rama de integración: sin ellas el pago era 0 % a 500 usuarios.
- La deuda principal es el snapshot de catalog en el camino crítico de la compra.

## 8. Reproducción

```bash
# 1. Stack
cd TP-TicketYa
QUEUE_BATCH_SIZE=1 QUEUE_INTERVAL_SECONDS=2 QUEUE_SCHEDULER_MS=500 \
  docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health

# 2. Evento de carga (imprime eventoId y sectorId)
python3 docs/evidencia/carga/setup-evento.py

# 3. Corrida
cd docs/evidencia/carga
HEAP="-Xms1g -Xmx2g" jmeter -n -t compra.jmx \
  -Jusers=500 -Jramp=60 -Jhost=localhost -Jport=8080 \
  -Jusuarios=usuarios.csv \
  -JeventoId=<eventoId> -JsectorId=<sectorId> \
  -l resultados/compra-500-final.jtl
```

## 9. Limitaciones

- Un solo cliente demo para todos los hilos; el límite por cliente del evento se elevó a
  1000 para que el cupo no fuera el límite. En producción cada cliente tendría su token.
- Los eventos de carga se crean sin alta demanda; la fila virtual se prueba en la suite E2E
  (`integration-tests`), no en esta corrida.
- La VM de Docker comparte CPU con JMeter; los percentiles absolutos no son comparables con
  hardware dedicado.
- No se midió la latencia de las notificaciones/recomendaciones asíncronas; la suite E2E
  valida que lleguen, no su throughput.
