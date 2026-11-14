# recommendation-service

Servicio de preferencias, historial de compras y recomendaciones de TicketYa sobre **MongoDB**.

- **Puerto:** 8084
- **Base:** `recommendation_db` (MongoDB 7, puerto 27017)
- **Responsable:** P5 — ver [`docs/equipo/entrega-2/p5-franco.md`](../docs/equipo/entrega-2/p5-franco.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 6
- **Estado:** Día 2 — tareas 1.7, 7.1 y 7.2. Las recomendaciones (7.3–7.5) se agregan en los días siguientes.

## Endpoints implementados

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/recommendations/preferences` | CLIENT | Preferencias del cliente autenticado |
| PUT | `/api/recommendations/preferences` | CLIENT | Upsert idempotente de tipos, artistas y ciudad |
| GET | `/internal/preferences/{clienteId}` | interno | Consulta para notification-service con `X-Internal-Token` |

La identidad llega desde el gateway en `X-User-Id` y `X-User-Role`; el cliente final usa JWT contra el gateway (tareas 2.1–2.7). La ruta `/api/recommendations/**` se agrega al gateway en la tarea de P4; hasta entonces el servicio se prueba directo en el puerto 8084.

### Consultar preferencias

```bash
curl http://localhost:8084/api/recommendations/preferences \
  -H "X-User-Id: 33333333-3333-4333-8333-333333333333" \
  -H "X-User-Role: CLIENT"
```

```json
{
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "tipos": ["FESTIVAL", "RECITAL"],
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "actualizadoEn": "2026-11-14T12:00:00Z"
}
```

Si el cliente nunca registró preferencias responde `200` con listas vacías, `ciudad: null` y `actualizadoEn: null`.

### Guardar o actualizar preferencias

```bash
curl -X PUT http://localhost:8084/api/recommendations/preferences \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 33333333-3333-4333-8333-333333333333" \
  -H "X-User-Role: CLIENT" \
  -d '{
    "tipos": ["RECITAL", "FESTIVAL"],
    "artistas": ["Los Piojos"],
    "ciudad": "Tandil"
  }'
```

Reglas:

- `tipos` se valida contra `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL` y `OTRO`; un valor desconocido responde `400 INVALID_EVENT_TYPE` con `errors[].field`.
- `ciudad` es obligatoria y no puede quedar vacía (`400 VALIDATION_ERROR`).
- Se recortan espacios y se eliminan duplicados (sin distinguir mayúsculas) de tipos y artistas.
- Un body con `clienteId` distinto del autenticado responde `403 NOT_PREFERENCE_OWNER`.
- El upsert usa un índice único por `clienteId`: no se crean documentos duplicados.

### Consulta interna

```bash
curl http://localhost:8084/internal/preferences/33333333-3333-4333-8333-333333333333 \
  -H "X-Internal-Token: ticketya-internal-dev"
```

Sin token o con un token distinto responde `401 UNAUTHORIZED`. Este endpoint no se expone por el gateway.

## Modelo MongoDB

| Colección | Documento | Índices |
|---|---|---|
| `preferencias` | `{clienteId, tipos[], artistas[], ciudad, actualizadoEn}` | único `clienteId` (`uk_preferencias_cliente`); `ciudad` (`idx_preferencias_ciudad`) |
| `historial_compras` | `{eventId, eventoId, clienteId, tipo, artistas[], ciudad, fechaEvento, ordenId, registradoEn}` | único `(eventId, clienteId, eventoId)` (`uk_historial_evento_cliente_evento`); `(clienteId, fechaEvento)`; `(eventoId, clienteId)` |
| `eventos_vistos` | `{_id: eventoId, nombre, tipo, artistas[], ciudad, fechaEvento, estado, actualizadoEn}` | `(ciudad, tipo)`; `fechaEvento` |

Los índices se crean al arrancar con `MongoIndexInitializer` (idempotente) y `spring.data.mongodb.auto-index-creation` queda deshabilitado. El detalle del modelo está en [`docs/modelo-datos.md`](../docs/modelo-datos.md#4-recommendation-service-recommendation_db-mongodb).

### Datos de prueba

`RecommendationSeedRunner` siembra de forma idempotente (con `$setOnInsert`, sin pisar cambios del cliente):

- Preferencias de `33333333-3333-4333-8333-333333333333` (Franco), `44444444-4444-4444-8444-444444444444` (Caro) y `55555555-5555-4555-8555-555555555555` (Tiago).
- Historial de compras de eventos `d0000000-…-0001`, `…-0002` y `…-0003`, que son los eventos publicados por el seed de catalog.
- Snapshots de esos tres eventos en `eventos_vistos`.

Se desactiva con `RECOMMENDATION_SEED_ENABLED=false`.

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8084` | Puerto HTTP |
| `MONGO_URI` | `mongodb://localhost:27017/recommendation_db` | Conexión MongoDB |
| `INTERNAL_TOKEN` | `ticketya-internal-dev` | Token de `/internal/**` compartido entre servicios |
| `RECOMMENDATION_ENSURE_INDEXES` | `true` | Crea los índices al arrancar |
| `RECOMMENDATION_SEED_ENABLED` | `true` | Activa los datos de prueba |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d recommendation-mongo
cd recommendation-service
mvn test
mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8084/actuator/health
```

Swagger UI: <http://localhost:8084/swagger-ui.html> · OpenAPI: <http://localhost:8084/v3/api-docs>

El OpenAPI exportado está en [`../openapi/recommendation-openapi.json`](../openapi/recommendation-openapi.json). Se regenera con `mvn -Dtest=OpenApiExportTest test`.

### Tests

- `PreferenceServiceTest`: normalización, upsert, tipo inválido, ciudad vacía, ownership y carrera de alta concurrente.
- `PreferenceControllerTest`: `200`, `400`, `401` y `403` de los endpoints públicos.
- `InternalPreferenceControllerTest`: token interno ausente, incorrecto y válido.
- `RecommendationMongoIntegrationTest` (Testcontainers `mongo:7`): crea y repite los índices, verifica el seed idempotente y que haya un solo documento de preferencias por cliente. Se omite si Docker no está corriendo.
- `OpenApiExportTest`: valida y exporta el OpenAPI.
