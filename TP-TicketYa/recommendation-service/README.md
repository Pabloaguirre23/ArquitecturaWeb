# recommendation-service

Microservicio de preferencias, historial y recomendaciones de TicketYa. Usa **MongoDB** porque
las preferencias son documentos con listas variables y el historial se consulta con
agregaciones por cliente, evento, artista y ciudad.

**Puerto:** `8084` · **Base:** `recommendation_db` (MongoDB) · **Prefijo público:** `/api/recommendations/**`

## Estado

Esqueleto de la Entrega 2 (tarea 1.7). Ya expone Actuator, Swagger y la conexión a MongoDB.
Las preferencias, el historial y las recomendaciones se implementan en las tareas 7.1 a 7.5.

## Endpoints planificados

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| PUT | `/api/recommendations/preferences` | CLIENT | Guardar o actualizar preferencias |
| GET | `/api/recommendations/preferences` | CLIENT | Consultar preferencias propias |
| GET | `/api/recommendations` | CLIENT | Recomendaciones personalizadas |
| GET | `/api/recommendations/events/{eventId}/similar` | CLIENT | Eventos similares por co-compra |
| GET | `/internal/preferences/followers` | interno | Clientes que siguen un artista en una ciudad |

## Modelo MongoDB planificado

| Colección | Documento | Índices |
|---|---|---|
| `preferencias` | `{clienteId, tipos[], artistas[], ciudad, updatedAt}` | único `clienteId`; `ciudad` |
| `historial_compras` | `{eventoId, clienteId, tipo, artistas[], ciudad, fechaEvento, ordenId, eventId}` | único `(eventId, clienteId, eventoId)`; `(clienteId, fechaEvento)`; `(eventoId, clienteId)` |
| `eventos_vistos` | snapshot mínimo para fallback y motivos | único `eventoId`; `ciudad`, `tipo` |

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8084` | Puerto HTTP |
| `MONGO_URI` | `mongodb://localhost:27017/recommendation_db` | Conexión a MongoDB |
| `GATEWAY_URL` | `http://localhost:8080` | Gateway para llamadas internas (cuando aplique) |
| `CATALOG_URL` | `http://localhost:8081` | Candidatos de eventos (cuando aplique) |

## Cómo ejecutar

```bash
# Desde la raíz del proyecto
docker compose -f docker-compose.dev.yml up -d recommendation-mongo

cd recommendation-service
mvn spring-boot:run
curl http://localhost:8084/actuator/health
```

Swagger: <http://localhost:8084/swagger-ui.html>

## Tests

```bash
cd recommendation-service
mvn test
```

El test de contexto usa Testcontainers con MongoDB; requiere Docker.

## Docker

```bash
cd recommendation-service
docker build -t ticketya/recommendation-service:local .
```
