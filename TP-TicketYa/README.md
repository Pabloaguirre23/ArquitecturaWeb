# TicketYa

TP grupal de Arquitectura Web (TUDAI 2026): plataforma de venta de entradas con microservicios Spring Boot.
Enunciado: [`docs/TPE-ArqWeb-TicketYa-2026.pdf`](docs/TPE-ArqWeb-TicketYa-2026.pdf).

Repositorio del curso: <https://github.com/Pabloaguirre23/ArquitecturaWeb> (este proyecto vive en `TP-TicketYa/`, rama `main`).

## Arquitectura

| Componente | Puerto | Base | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | — | Punto de entrada único, ruteo e identidad |
| `catalog-service` | 8081 | `catalog_db` (5433) | Lugares, sectores, eventos, precios, publicación y búsqueda |
| `inventory-service` | 8082 | `inventory_db` (5434) | Disponibilidad, reservas temporales y no doble venta |
| `orders-service` | 8083 | `orders_db` (5435) | Órdenes, pago mock, entradas/QR, devoluciones y cancelación |
| `recommendation-service` | 8084 | `recommendation_db` (27017, MongoDB) | Preferencias, historial y recomendaciones |
| `notification-service` | 8085 | `notification_db` (5436) | Notificaciones asíncronas con reintentos y DLQ |
| `rabbitmq` | 5672 / 15672 | — | Bus de eventos (`orden.pagada` → historial) |
| `queue-service` | 8086 | Redis 7 (6379) | Fila virtual: tandas, posición y token de compra |
| `loki` / `promtail` / `grafana` | 3100 / — / 3001 | — | Logs JSON centralizados y consulta por `correlationId` |
| `web` (opcional) | 3000 | — | UI React/Vite que Nginx proxya al gateway |

Son **seis microservicios** (catalog, inventory, orders, recommendation, notification y queue)
más el gateway. Cada servicio es un proyecto Maven independiente. La comunicación entre
servicios es REST por `/internal/**` y eventos por RabbitMQ; el cliente siempre entra por el
gateway. `queue-service` guarda la fila en Redis.

## Requisitos

- **Java 21** (Temurin recomendado)
- **Maven 3.9+**
- **Docker Desktop** (para PostgreSQL 16, MongoDB 7, RabbitMQ 3.13 y Redis 7)
- Git

Verificar:

```bash
java -version          # debe decir 21
mvn -version           # debe usar Java 21
docker --version
```

Si Maven usa otra versión de Java, configurá `JAVA_HOME` apuntando a tu JDK 21. En macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Cómo ejecutar

### 1. Bases de datos

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

Las cuatro bases PostgreSQL, Redis, RabbitMQ y MongoDB deben quedar `healthy`. Flyway aplica las migraciones al levantar cada servicio; MongoDB crea sus índices al arrancar `recommendation-service`.

Para reiniciar sin datos:

```bash
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
```

### 2. Todo con Docker (integral)

El compose integral levanta las bases, Redis, MongoDB, RabbitMQ, los seis microservicios, el
gateway y la stack de observabilidad:

```bash
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
curl http://localhost:8080/actuator/health
```

Solo el gateway publica el puerto `8080`; las bases y brokers quedan en la red interna de
Docker. La UI se publica en `3000`, Grafana en `http://localhost:3001` (usuario `admin`,
password `GRAFANA_ADMIN_PASSWORD`, default `admin`) y Loki en `3100` para consultas directas. Para
reiniciar sin datos:

```bash
docker compose -f docker-compose.yml down -v
```

Promtail lee los logs JSON de stdout de cada contenedor y los manda a Loki. En Grafana,
`Explore → Loki` permite filtrar por `{container="ticketya-full-orders-service-1"}` y buscar
`|= "<correlationId>"`. El `correlationId` viaja desde el gateway en `X-Correlation-Id` y cada
servicio lo deja en el MDC y en la línea `request completada`.

La UI opcional vive en `web/` y no forma parte del backend entregado.

### 3. Servicios con Maven

En seis terminales distintas:

```bash
cd catalog-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd orders-service && mvn spring-boot:run
cd recommendation-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd queue-service && mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8086/actuator/health
```

### 4. Gateway

```bash
cd api-gateway && mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

### 5. Swagger

- Catalog: <http://localhost:8081/swagger-ui.html>
- Inventory: <http://localhost:8082/swagger-ui.html>
- Orders: <http://localhost:8083/swagger-ui.html>
- Recommendation: <http://localhost:8084/swagger-ui.html>
- Notification: <http://localhost:8085/swagger-ui.html>
- Queue: <http://localhost:8086/swagger-ui.html>

OpenAPI crudo: `http://localhost:<puerto>/v3/api-docs`.

### 6. Interfaz web (opcional)

La UI del cliente vive en [`web/`](web/) y es un extra opcional de la Entrega 2. El compose
integral la publica en `http://localhost:3000` y Nginx proxya `/api` al gateway real. Incluye
login JWT, búsqueda, detalle con asientos, checkout, mis entradas y la pantalla **Para vos**
con preferencias, recomendaciones explicables y eventos similares; además, las pantallas de
**control de acceso** (escaneo de QR, 6.4) y **reportes del organizador** (6.5) para staff.

Para desarrollo con recarga en caliente, en modo demo sin backend:

```bash
cd web
npm install
npm run dev
# http://localhost:5173
# Demo: cliente@ticketya.test / demo
```

Para desarrollo contra el gateway real, copiar `web/.env.example` a `web/.env`, poner
`VITE_USE_MOCKS=false` y levantar el gateway en `8080`. El detalle está en
[`web/README.md`](web/README.md).

## Variables de entorno

Los servicios traen valores por defecto para desarrollo. Se pueden pisar con:

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | 8080/8081/8082/8083/8084/8085/8086 | Puerto del servicio |
| `DB_URL` | `jdbc:postgresql://localhost:<5433/5434/5435>/<db>` | Conexión JDBC |
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5436/notification_db` | Conexión JDBC de notification |
| `DB_USER` | `ticketya` | Usuario de la base |
| `DB_PASSWORD` | `ticketya` | Password de la base |
| `MONGO_URI` | `mongodb://localhost:27017/recommendation_db` | Conexión MongoDB de recommendation |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker de eventos |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `ticketya` / `ticketya` | Credenciales del broker |
| `INTERNAL_TOKEN` | `ticketya-internal-dev` | Token de los endpoints `/internal/**` |
| `QUEUE_TOKEN_SECRET` | `ticketya-queue-dev-secret-key-32b` | Firma del token de fila (queue e inventory) |
| `RECOMMENDATION_SEED_ENABLED` | `true` | Seed de preferencias e historial |
| `RECOMMENDATION_QUEUE` | `recommendation.history` | Cola del consumidor de `orden.pagada` |
| `CATALOG_URL` | `http://localhost:8081` | Gateway → catalog |
| `INVENTORY_URL` | `http://localhost:8082` | Gateway → inventory |
| `ORDERS_URL` | `http://localhost:8083` | Gateway → orders |
| `RECOMMENDATION_URL` | `http://localhost:8084` | Gateway → recommendation |
| `NOTIFICATION_URL` | `http://localhost:8085` | Gateway → notification |
| `CATALOG_SCHEDULER_FINALIZACION_MS` | `60000` | Frecuencia del job de eventos vencidos |
| `INVENTORY_VENCIMIENTO_MS` | `30000` | Frecuencia del job de reservas vencidas |
| `DB_POOL_SIZE` | `20` (`40` en compose) | Pool Hikari de orders e inventory |
| `DB_CONNECTION_TIMEOUT_MS` | `3000`/`5000` | Timeout para obtener conexión |
| `QUEUE_SCHEDULER_MS` | `5000` | Frecuencia del scheduler de tandas |
| `QUEUE_RETENTION` | `7d` | TTL de las claves Redis de un evento al cerrar la fila |
| `RECOMMENDATION_SEED_ENABLED` | `true` | Carga preferencias e historial demo en MongoDB; `false` para datos limpios |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Password del usuario `admin` de Grafana |
| `JWT_SECRET` / `JWT_TTL` | dev / `8h` | Firma y vigencia del JWT del gateway |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Orígenes permitidos por CORS en el gateway |

Ver [`.env.example`](.env.example). No se commitea `.env`.

`INTERNAL_TOKEN` y `QUEUE_TOKEN_SECRET` tienen **defaults de desarrollo** en
`docker-compose.yml` y en `.env.example` (se interpolan como `${VAR:-...}`). No son secretos de
producción: para cualquier entorno que no sea la VM local, definirlos en `.env` y no reutilizar
los valores de ejemplo. Los servicios internos y el gateway también aceptan las variables por
entorno; el compose es el que provee el default cuando falta.
`JWT_SECRET` **no tiene default en el gateway**: si falta, el gateway no arranca (fail-fast). El
compose le pasa el valor de desarrollo; fuera del compose hay que definirlo.

> **Decisión sobre los `:-` del compose:** se mantienen a propósito para que el docente pueda
> hacer `docker compose up --build` sin crear un `.env`. El gateway conserva su fail-fast si la
> variable no llega; el compose es explícitamente un entorno de demo. Para un despliegue real,
> reemplazar los `${VAR:-...}` por `${VAR:?...}` o inyectar los secretos desde el orquestador.

## Prueba rápida del gateway

Las lecturas públicas no piden identidad:

```bash
curl http://localhost:8080/api/catalog/events
```

Las rutas protegidas exigen JWT: primero se inicia sesión contra `/api/auth/login` y el token viaja como `Authorization: Bearer` (el gateway descarta cualquier `X-User-*` que mande el cliente):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ticketya.test","password":"demo"}'
# → { "token": "...", "role": "CLIENT", "email": "...", "nombre": "...", "expiresAt": "..." }

TOKEN=<pegar el token>

curl -X POST http://localhost:8080/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"eventoId":"...","items":[]}'
```

Sin token, el gateway responde 401 en formato RFC 7807. El contrato completo está en [`docs/api-contract.md`](docs/api-contract.md).

## Estructura

| Carpeta | Contenido |
|---|---|
| `catalog-service/` | Eventos, lugares, sectores, búsqueda |
| `inventory-service/` | Reservas y anti doble venta |
| `orders-service/` | Compra, pago mock, QR, devoluciones |
| `recommendation-service/` | Preferencias, historial y recomendaciones (MongoDB) |
| `notification-service/` | Notificaciones asíncronas (PostgreSQL + RabbitMQ + DLQ) |
| `api-gateway/` | Punto de entrada único |
| `queue-service/` | Fila virtual sobre Redis |
| `web/` | Interfaz web opcional del cliente (React + Vite) |
| `openspec/` | Especificaciones (propuesta → specs → diseño → tareas) |
| `docs/` | Contrato, plan, checklist, arquitectura y briefs del equipo |
| `docker-compose.dev.yml` | PostgreSQL 16 por servicio, MongoDB 7 y RabbitMQ 3.13 |
| `docker-compose.yml` | Compose integral: seis servicios, gateway, UI web opcional, PostgreSQL, MongoDB, RabbitMQ y Redis |

## Tests

Cada servicio se testea por separado:

```bash
cd catalog-service && mvn test
cd inventory-service && mvn test
cd orders-service && mvn test
cd recommendation-service && mvn test
cd notification-service && mvn test
cd queue-service && mvn test
cd api-gateway && mvn test
```

Las pruebas de integración de catalog, concurrencia de inventory e idempotencia de orders usan Testcontainers y requieren Docker.

## Equipo y flujo de trabajo

- [Briefs por integrante](docs/equipo/README.md)
- [Cómo trabajamos](CONTRIBUTING.md)
- [Plan día por día — Entrega 1](docs/plan-entrega-1.md)
- [Checklist verificable de la Entrega 1](docs/checklist-entrega-1.md)
- [Plan día por día — Entrega 2](docs/plan-entrega-2.md)
- [Checklist verificable de la Entrega 2](docs/checklist-entrega-2.md)
- [Briefs de la Entrega 2](docs/equipo/entrega-2/README.md)
- [Índice de documentación](docs/README.md)
- [Cambio OpenSpec de la Entrega 1](openspec/changes/entrega-1-core/)
- [Cambio OpenSpec de la Entrega 2](openspec/changes/entrega-2-completo/)

Reglas:

1. `main` requiere Pull Request y al menos 1 aprobación (la protección está activa desde el 20/11; tarea 1.1).
2. Un dueño por carpeta de microservicio.
3. Rama por tarea: `feat/<servicio>-<tema>`.
4. La documentación y los tests van en el mismo PR que el código.

## Metodología

Desarrollo guiado por especificaciones con [OpenSpec](https://github.com/Fission-AI/OpenSpec). El change activo es `entrega-2-completo`:

```bash
openspec validate entrega-2-completo --strict
openspec status --change entrega-2-completo
```

## Estado

Entrega 1: núcleo implementado (publicación, búsqueda, compra con reserva y QR, no doble
venta, devoluciones y cancelación). El estado verificable y las observaciones abiertas están
en [`docs/checklist-entrega-1.md`](docs/checklist-entrega-1.md); la estrategia y la evidencia
de pruebas, en [`docs/pruebas.md`](docs/pruebas.md).

Entrega 2: JWT, fila virtual, saga/outbox, acceso QR, reportes, notificaciones,
recomendaciones, cache, Docker y CI están integrados; el estado verificable está en
[`docs/checklist-entrega-2.md`](docs/checklist-entrega-2.md), la estrategia en
[`docs/pruebas.md`](docs/pruebas.md) y la prueba de carga en
[`docs/informe-carga.md`](docs/informe-carga.md). La suite E2E corre 8/8 sin omitidos contra
el compose. La branch protection de `main` exige 1 aprobación y los 10 checks del CI, y el
tag `entrega-2` apunta al commit entregado. Queda solo archivar el change OpenSpec después
de la entrega. Las suites con Docker (566 tests), el E2E (8/8), Newman (30 requests,
37 assertions), el clone limpio y la consulta real en Loki están documentados en
`docs/evidencia/entrega-2-revision-docker.txt`. Entrega: 23/11/2026.

Entrega: <https://github.com/Pabloaguirre23/ArquitecturaWeb/tree/main/TP-TicketYa>

## Datos de prueba

Los seeds de Flyway (`V2__seed.sql` en catalog e inventory) crean:

| Dato | ID | Detalle |
|---|---|---|
| Lugar | `a0000000-0000-4000-8000-000000000001` | Estadio Municipal de Tandil |
| Lugar | `a0000000-0000-4000-8000-000000000002` | Complejo Cultural Universitario |
| Evento | `d0000000-0000-4000-8000-000000000001` | Festival de Rock Tandil 2027, 14/03/2027 |
| Evento | `d0000000-0000-4000-8000-000000000002` | El Avaro — Teatro Universitario, 18/04/2027 |
| Evento | `d0000000-0000-4000-8000-000000000003` | Arquitectura Web 2027 — Charla abierta, 20/05/2027 |

Usuarios de prueba de la Entrega 2: todos usan la contraseña `demo` y el gateway deriva
`X-User-Id`/`X-User-Role` del JWT (los headers `X-User-*` externos se descartan):

| Rol | Email (`password`: `demo`) | `X-User-Id` derivado del JWT |
|---|---|---|
| ADMIN | `admin@ticketya.test` | `11111111-1111-4111-8111-111111111111` |
| ORGANIZER | `organizador@ticketya.test` | `22222222-2222-4222-8222-222222222222` |
| CLIENT | `cliente@ticketya.test` | `33333333-3333-4333-8333-333333333333` |
| ACCESS_STAFF | `acceso@ticketya.test` | `55555555-5555-4555-8555-555555555555` |

El gateway mantiene estos cuatro usuarios **en memoria** (`DemoUserStore`), con la contraseña
`demo` hasheada con BCrypt. No hay `auth_db` ni alta de cuentas: es una simplificación de demo
para el TP (ver ADR-020 en [`docs/decisiones.md`](docs/decisiones.md)).

### Roles y navegación

Después del login, cada rol aterriza en su servicio y ve solo lo suyo:

| Rol | Home | Navegación |
|---|---|---|
| `CLIENT` | `/` | Buscar, Para vos, Mis entradas; puede comprar |
| `ORGANIZER` | `/reports` | Reportes |
| `ACCESS_STAFF` | `/access` | Control de acceso |
| `ADMIN` | `/reports` | Control de acceso, Reportes |

La compra y “Mis entradas” están limitadas a `CLIENT`; el `ADMIN` ve ambos paneles de staff.
El catálogo público sigue disponible por URL para staff, pero no se muestra como solapa.
En “Para vos”, los tipos de evento filtran la lista al instante (“Ver todos” limpia el filtro) y
“Guardar preferencias” persiste la selección para el ranking.

Los asientos numerados usan IDs determinísticos: `c0000000-0000-4000-8000-[sector][fila][numero]000000`. La colección [`postman/TicketYa.postman_collection.json`](postman/TicketYa.postman_collection.json) recorre los flujos de catálogo y compra (aprobada, rechazada, pago lento y cancelación) y guarda los IDs que crea.
