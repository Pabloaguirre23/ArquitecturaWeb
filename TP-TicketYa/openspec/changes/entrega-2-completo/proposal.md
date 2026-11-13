## Why

La Entrega 1 dejó el núcleo de TicketYa funcionando (publicación, búsqueda, compra sin doble venta, devoluciones y cancelación), pero todavía no cubre los ítems 6, 8, 10, 11 y 12 del enunciado ni los requisitos no funcionales de la Entrega 2. Sin fila virtual, control de acceso, recomendaciones, notificaciones, reportes, JWT, NoSQL, cache, resiliencia, log centralizado y Docker, el sistema no está completo y no puede soportar la apertura de un evento masivo de punta a punta.

## What Changes

- **Autenticación y autorización con JWT**: login en el gateway, token con rol y email, validación en el borde, roles `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`, y matriz de acceso por endpoint. **BREAKING**: se elimina la identidad por `X-User-Id`/`X-User-Role` enviada por el cliente; el gateway sigue propagando esos headers hacia los servicios, pero los deriva del JWT.
- **Fila virtual para eventos de alta demanda**: nuevo `queue-service` con Redis, posición en tiempo real por polling, habilitación por tandas y token temporal que inventory-service exige para reservar.
- **Control de acceso con QR**: validación atómica de entradas por personal de acceso, marcado `UTILIZADA` y rechazo de reutilizaciones y de entradas de otro evento.
- **Preferencias y recomendaciones**: nuevo `recommendation-service` con MongoDB, registro de preferencias, historial de compras por eventos y recomendaciones por gustos, ciudad y “quienes compraron X también compraron Y”.
- **Notificaciones asíncronas**: nuevo `notification-service` que consume eventos por RabbitMQ y envía email/push simulado al confirmar una compra, al cancelar o cambiar un evento y al publicarse un evento de un artista favorito.
- **Reportes para organizadores**: entradas vendidas y devueltas por sector, ocupación y recaudación por evento, con autorización por dueño del evento.
- **Consistencia y compensaciones**: saga de compra explícita, outbox transaccional, eventos de dominio idempotentes y reintentos; consistencia fuerte dentro de cada servicio y eventual entre servicios.
- **Resiliencia**: timeouts, reintentos acotados, circuit breaker y fallback en las llamadas a pasarela de pagos, inventory, catalog y notification.
- **Cache**: Redis para catálogo y búsqueda, con TTL, invalidación y exclusión explícita de disponibilidad de asientos.
- **Observabilidad**: log JSON por solicitud (endpoint, usuario, estado, duración), `X-Correlation-Id` propagado, bitácora de operaciones críticas y stack Loki/Promtail/Grafana para consulta centralizada.
- **Despliegue**: un Dockerfile por microservicio y un `docker-compose.yml` único con servicios, bases, Redis, MongoDB, RabbitMQ y observabilidad.
- **Testing**: pruebas de integración entre servicios, matriz de seguridad por rol, prueba de carga del flujo de compra con informe y colección Postman/Newman actualizada.

## Capabilities

### New Capabilities

- `seguridad-jwt`: login, emisión y validación de JWT, roles y matriz de autorización por endpoint.
- `fila-virtual`: alta demanda, ingreso a la fila, posición, tandas, token temporal y exigencia en la reserva.
- `control-acceso-qr`: validación de QR en el ingreso, uso único y concurrencia de escaneos.
- `preferencias-recomendaciones`: preferencias del cliente, historial y sugerencias personalizadas sobre MongoDB.
- `notificaciones`: eventos de compra, cancelación, cambio y artista favorito; envío asíncrono, reintentos y DLQ.
- `reportes-organizador`: reporte por evento y sector con ventas, devoluciones, ocupación y recaudación.
- `consistencia-saga`: saga de compra, compensaciones, outbox e idempotencia de eventos de dominio.
- `resiliencia-servicios`: timeouts, reintentos, circuit breaker y fallback ante dependencias externas.
- `cache-catalogo`: datos cacheados, TTL, invalidación y datos que nunca se cachean.
- `observabilidad`: log de solicitudes, bitácora de operaciones críticas y correlación entre servicios.
- `despliegue-contenedores`: contenedor por servicio, compose integral, healthchecks y configuración por entorno.

### Modified Capabilities

- `gestion-eventos-lugares`: alta demanda, edición de eventos publicados, cierre de venta, datos internos para reportes y publicación de eventos de dominio.
- `busqueda-eventos`: cache de resultados y detalle, invalidación y exclusión de disponibilidad.
- `reserva-y-compra`: token de fila virtual, email del comprador, saga/outbox y resiliencia en las dependencias.
- `devoluciones-cancelacion`: notificación a compradores, eventos de dominio y compensaciones idempotentes.

## Impact

**Servicios nuevos:** `queue-service` (Redis), `recommendation-service` (MongoDB) y `notification-service` (PostgreSQL + RabbitMQ).

**Servicios modificados:** `api-gateway` (JWT, roles y rutas nuevas), `catalog-service` (alta demanda, edición, cache, outbox y datos de reporte), `inventory-service` (token de fila, cancelación de reserva y resiliencia), `orders-service` (acceso QR, reportes, saga/outbox, email y resiliencia).

**Infraestructura nueva:** Redis 7, MongoDB 7, RabbitMQ 3.13, Loki, Promtail y Grafana; un Dockerfile por servicio y `docker-compose.yml` integral.

**Dependencias nuevas:** Spring Security + JJWT en gateway, Spring Data MongoDB en recommendation, Spring Data Redis en catalog/queue, Spring AMQP en productores y consumidores, Resilience4j en clientes externos, Logstash Logback Encoder y Micrometer.

**APIs:** se agregan endpoints de auth, fila, acceso, preferencias, recomendaciones, notificaciones y reportes; se actualizan OpenAPI, contrato humano y colección Postman. Los endpoints internos se protegen con `X-Internal-Token`.

**Datos:** nuevas bases `notification_db`, colecciones MongoDB y claves Redis; migraciones Flyway nuevas para outbox, auditoría, email, acceso y reportes. No se editan migraciones ya aplicadas.

**Fuera de alcance:** interfaz gráfica (se evalúa solo como extra si todo lo comprometido está verde), pasarela de pagos real, proveedor real de email/push, fila virtual con WebSockets/tiempo real estricto, alta disponibilidad, Kubernetes y despliegue en nube. Las simplificaciones de fila virtual y recomendaciones se documentan y justifican.
