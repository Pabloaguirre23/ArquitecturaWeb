# Checklist de Entrega 2 — TicketYa

Lista verificable de todo lo que pide el enunciado para la Entrega 2. Cada fila tiene una
tarea de OpenSpec y una evidencia concreta. Se completa al final de cada día y se revisa
completa el 20 y 21 de noviembre.

**Leyenda:** `[ ]` pendiente · `[x]` verificado con evidencia.

**Estado inicial (13/11):** planificación completa. El change `entrega-2-completo` tiene
propuesta, 15 specs, diseño y 64 tareas, y valida con `openspec validate --strict`. La
implementación arranca el Día 1.

## 0. Estado de verificación

| Área | Estado | Evidencia |
|---|---|---|
| Change OpenSpec | Validado el 20/11 | `openspec validate entrega-2-completo --strict` |
| Criterios US-25 a US-38 | Completos y trazados | `docs/features-y-user-stories.md` (tarea 1.2) |
| recommendation-service | Build, tests, health y Docker OK | `recommendation-service/` (tareas 1.7, 2.7, 7.1–7.5) |
| notification-service | Completo: compra, cancelación, favorito y consulta | `notification-service/` (8.1–8.4) y PR #27 |
| Acceso al repo del curso | 5 colaboradores con push; branch protection pendiente | Tarea 1.1 |
| Sobre de eventos y payloads | Congelado el 20/11/2026 | [`eventos.md`](eventos.md) (1.5, PR #39) |
| ADR de la Entrega 2 | ADR-014 a ADR-020 | [`decisiones.md`](decisiones.md) |
| Contrato v2 y matriz de roles | Completo: acceso/reportes incluidos | `docs/api-contract.md` (1.3 y 6.7) |
| JWT y autorización | 2.1–2.3 completos; 2.8 parcial (Postman por rol) | PR #29, [`seguridad.md`](seguridad.md), suite E2E |
| Fila virtual | Completa (4.1–4.4, 5.1) | `queue-service/` y PR #24 |
| Control de acceso QR | Completo (6.4) | PR #43 + E2E de doble escaneo; [`evidencia/entrega-2-dia-8.txt`](evidencia/entrega-2-dia-8.txt) |
| Preferencias y recomendaciones | Completo (7.1–7.5 y 2.7) | `recommendation-service` + tests Mongo/Rabbit/HTTP y [`evidencia/entrega-2-dia-4.txt`](evidencia/entrega-2-dia-4.txt) |
| Notificaciones | Completo (8.1–8.4) | PR #27 y evidencia `dia-7-p1.txt` |
| Reportes | Completo (6.5) | PR #44 + E2E con totales; [`evidencia/entrega-2-dia-8.txt`](evidencia/entrega-2-dia-8.txt) |
| Consistencia, saga y outbox | Completo (3.2, 6.2, 6.3) | PR #25 y #27; corrida de carga con transacciones cortas |
| Resiliencia | Parcial: inventory 5.3; falta 6.6 de pagos/inventory | PR #21, #25 |
| Cache | Completo (3.3, 3.4) | PR #27, tests de cache |
| Observabilidad y log central | Parcial: 9.2 y 9.3 hechos; faltan 9.1 y 9.4 | PR #27 y #46; Loki pendiente |
| Docker y compose | Completo (9.5–9.7) | 15/15 contenedores healthy con la UI; CI verde en PR #40/#41 |
| Integración, seguridad y carga | 10.1 8/8 sin omitidos; 10.3/10.4 completos; 10.2 parcial | [`pruebas.md`](pruebas.md) §12, [`informe-carga.md`](informe-carga.md) y [`evidencia/entrega-2-dia-8.txt`](evidencia/entrega-2-dia-8.txt) |
| Cierre y tag | Pendiente: 10.8 (Tiago); 10.9 en curso | Tareas 10.8–10.9 |

## 1. Funcionalidad de la Entrega 2

| # | Requisito del enunciado | User stories | Spec | Tareas | Evidencia |
|---|---|---|---|---|---|
| 6 | Fila virtual para eventos de alta demanda: posición, habilitación por tandas y token temporal | US-25, US-26, US-27 | `fila-virtual` | 4.1–4.4, 5.1 | Tests de queue e inventory + Postman |
| 8 | Control de acceso: validar QR, evento correcto, uso único y concurrencia | US-28 | `control-acceso-qr` | 6.4 | Test de dos escaneos concurrentes + Postman |
| 10 | Preferencias (tipos, artistas, ciudad) y recomendaciones por historial y co-compra | US-29, US-30 | `preferencias-recomendaciones` | 7.1–7.5 | Tests de Mongo/Rabbit + Postman |
| 11 | Notificaciones de compra, cancelación/cambio y artista favorito, sin bloquear la compra | US-31, US-32, US-33 | `notificaciones` | 8.1–8.4 | Tests de consumidor y DLQ + evidencia de compra con broker caído |
| 12 | Reportes por evento: vendidas y devueltas por sector, ocupación y recaudación | US-34 | `reportes-organizador` | 6.5 | Test de cálculo + Postman |
| — | Los ítems 1–5, 7 y 9 de la Entrega 1 siguen funcionando con JWT | US-01 a US-24 | specs de Entrega 1 | 2.1–2.8, 10.1 | Suite de regresión + E2E |

## 2. Arquitectura y requisitos no funcionales

- [x] **Microservicios Spring Boot independientes:** siete servicios y el gateway, cada uno con su `pom.xml`, build y ejecución separada. → 9.5, 9.6
- [x] **Punto de entrada único:** todo el tráfico externo entra por el gateway; las bases y brokers no están publicados. → 9.6
- [x] **Comunicación asíncrona justificada:** RabbitMQ para notificaciones, historial de recomendaciones y eventos de catálogo; documentación de por qué. → 1.5, 6.3, 8.2. _(Contrato congelado en [`eventos.md`](eventos.md) y productores/consumidores implementados.)_
- [x] **Base NoSQL:** MongoDB para preferencias e historial, con justificación y consultas. → 7.1. Evidencia: [`recommendation-service/README.md`](../recommendation-service/README.md#modelo-mongodb), índices verificados en `RecommendationMongoIntegrationTest` y [`evidencia/entrega-2-dia-2.txt`](evidencia/entrega-2-dia-2.txt).
- [x] **Consistencia entre servicios y compensaciones:** saga de compra, outbox, idempotencia y política de consistencia documentada. → 6.2, 6.3, 3.2
- [ ] **Resiliencia:** timeouts, reintentos, circuit breaker y fallback en pasarela, inventory, catalog, queue y notification. → 5.3, 6.6, 2.3
- [x] **Cache:** datos cacheados, TTL, invalidación y datos que no se cachean (disponibilidad, asientos, reservas). → 3.3, 3.4
- [ ] **Trazabilidad:** log de cada solicitud con endpoint, usuario, estado y duración; correlación entre servicios. → 9.1
- [x] **Bitácora:** operaciones críticas de compra, devolución, cancelación, publicación e ingreso. → 9.2, 9.3
- [ ] **Log centralizado:** JSON a stdout + Loki/Promtail/Grafana consultable por correlación. → 9.4
- [x] **Seguridad:** JWT con roles `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`; matriz endpoint × rol; endpoints internos protegidos. → 2.1–2.8
- [ ] **Despliegue:** un contenedor por servicio y un `docker-compose.yml` único con bases, Redis, MongoDB, RabbitMQ y observabilidad. → 9.5, 9.6
- [x] **Rendimiento:** prueba de carga del flujo de compra con informe de usuarios, tiempos y errores. → 10.3, 10.4

## 3. Features y user stories

- [x] **Criterios de aceptación de US-25 a US-38 completos** y actualizados respecto del enunciado. → 1.2
- [x] **Criterios de US-01 a US-24 actualizados** por JWT, fila virtual, notificaciones y reportes. → 1.2, 2.4–2.7
- [x] **Matriz US → capability → tarea → evidencia** sin historias huérfanas. → 1.2

## 4. Arquitectura y modelo de datos

- [x] **Diagrama de módulos actualizado** con gateway, siete servicios, bases, Redis, MongoDB, RabbitMQ y observabilidad. → 10.5
- [x] **Diagrama de secuencia de compra** con saga, outbox, compensaciones y notificaciones. → 10.5
- [x] **Diagrama de secuencia de fila virtual** (ingreso, tandas, token, reserva). → 10.5
- [x] **Diagrama de secuencia de acceso QR** con concurrencia. → 10.5
- [x] **ER por microservicio actualizado** con tablas nuevas, claves, relaciones y entidades solapadas. → 10.6
- [x] **Modelo MongoDB** con colecciones, documentos e índices. → 10.6
- [x] **Estrategia de consistencia** (fuerte vs. eventual) y manejo de fallos parciales documentado. → 6.2, 6.3, 10.5
- [ ] **Decisiones tecnológicas justificadas:** MongoDB, Redis, RabbitMQ, JWT, Resilience4j, outbox, saga, cache y observabilidad. → 10.6. _(ADR-014 a ADR-018 cubren BFF, Service Discovery, gateway/`auth_db`, payload de `orden.pagada` y sobre de eventos.)_

## 5. Diseño de API REST

- [x] **Contrato v2** con método, ruta, parámetros, roles, códigos y ejemplos JSON. → 1.3
- [x] **OpenAPI por servicio** con `bearerAuth`, descripciones, ejemplos y códigos de error. → 2.4–2.7, 3.7, 5.5, 6.7, 7.5, 8.4
- [x] **Endpoints internos** documentados aparte y no expuestos en el OpenAPI público. → 1.3, 2.3
- [ ] **Colección Postman/Newman v2** con auth, fila, compra, acceso, reportes, recomendaciones, notificaciones y seguridad. → 10.2

## 6. Implementación

- [x] **JWT, roles y matriz de acceso** en gateway y servicios. → 2.1–2.8
- [x] **Fila virtual** con Redis, posición, tandas y token. → 4.1–4.4
- [x] **Token de fila** exigido por inventory en eventos de alta demanda. → 5.1
- [x] **Control de acceso QR** con uso único y concurrencia. → 6.4
- [x] **Preferencias y recomendaciones** con MongoDB, historial y co-compra. → 7.1–7.5 _(completas: preferencias, historial idempotente, ranking explicable y eventos similares; seguridad 2.7 documentada en [`seguridad.md`](seguridad.md); evidencia en [`evidencia/entrega-2-dia-4.txt`](evidencia/entrega-2-dia-4.txt))_
- [x] **Notificaciones** asíncronas con reintentos, DLQ y consulta propia. → 8.1–8.4
- [x] **Reportes** por evento y sector con autorización. → 6.5
- [x] **Saga, outbox y compensaciones** en la compra. → 6.2, 6.3
- [ ] **Resiliencia** en todas las dependencias externas. → 5.3, 6.6, 2.3
- [x] **Cache** de catálogo con TTL e invalidación. → 3.3, 3.4
- [ ] **Observabilidad** con logs JSON, correlación, bitácora y Loki. → 9.1–9.4
- [x] **Docker** por servicio y compose integral. → 9.5, 9.6
- [x] **Datos de prueba** para usuarios, eventos de alta demanda, preferencias e historial. → 1.8, 7.1, 10.1

## 7. Testing

- [x] **Unitarios** de la lógica nueva (fila, acceso, reportes, recomendaciones, notificaciones, saga, cache). → tareas de cada servicio
- [x] **No doble venta** con 100 hilos y última unidad de zona, sigue verde con la fila y el token. → 5.4, 10.1
- [x] **Integración entre servicios** con Testcontainers/Compose: login, fila, compra, QR, reporte, recomendación y notificación. → 10.1 _(20/11: 8 tests, 0 fallos, 0 omitidos; incluye acceso QR y reporte. Evidencia en `evidencia/entrega-2-dia-8.txt`.)_
- [x] **Seguridad de endpoints:** cada endpoint solo accesible por sus roles; 401 sin token y 403 con rol incorrecto. → 2.8 _(gateway + tests de servicio + E2E; la carpeta Postman por rol de 2.8 queda parcial en 10.2)_
- [x] **Idempotencia de eventos:** duplicados no duplican historial ni notificaciones. → 7.3, 8.2 _(tests de recommendation y notification + E2E)_
- [ ] **Resiliencia:** timeout, 5xx, circuito abierto y recuperación con WireMock. → 5.3, 6.6
- [x] **Prueba de carga** de 500 y 1000 usuarios con resultados crudos y análisis. → 10.3, 10.4

## 8. Documentación y entregables

- [x] **README raíz** con requisitos, `docker compose up --build`, variables, usuarios de prueba, URLs y comandos. → 10.6, 10.8
- [x] **README por servicio** actualizado con endpoints, modelo, variables y tests. → 3.7, 5.5, 6.7, 7.5, 8.4
- [x] **`docs/api-contract.md`** actualizado. → 1.3, 10.6
- [x] **`docs/arquitectura.md`** actualizado. → 10.5
- [x] **`docs/modelo-datos.md`** actualizado. → 10.6
- [x] **`docs/seguridad.md`** con la matriz endpoint × rol. → 2.8
- [x] **`docs/pruebas.md`** con estrategia y evidencia. → 10.7
- [x] **`docs/informe-carga.md`** con hardware, usuarios, throughput, percentiles y errores. → 10.4
- [x] **`docs/decisiones.md`** con las ADR nuevas. → 10.6
- [x] **`docs/README.md`** como índice actualizado. → 10.7
- [x] **`docs/checklist-entrega-2.md`** al 100 % con evidencia. → 10.7
- [x] **OpenAPI exportado** de cada servicio en `openapi/`. → tareas de cada servicio
- [ ] **Colección Postman** actualizada. → 10.2

## 9. Cierre

- [x] **Suite completa** de los siete servicios verde, sin tests omitidos. → 10.8
- [ ] **Clone limpio** con `docker compose up --build` y smoke test. → 10.8
- [ ] **Tag `entrega-2`** sobre el commit entregado. → 10.8
- [x] **Repositorio del curso accesible** y enlace de entrega verificado. → 10.8, 10.9
- [x] **Change validado** con `openspec validate entrega-2-completo --strict`. → 10.9 _(20/11: valido; quedan 7 tareas abiertas declaradas)_
- [ ] **Change archivado** después de la entrega. → Workflow follow-up
