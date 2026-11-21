# Seguridad — matriz de acceso

Este documento centraliza la matriz endpoint × rol y la evidencia de `401/403/200`.
La sección de `recommendation-service` corresponde a la tarea **2.7** (P5). Inventory y queue
corresponden a la tarea **2.5** (P2). Las filas de catalog, orders y notification las completa
P4 en la tarea 2.8.

## recommendation-service

El gateway valida el JWT (HS256) y descarta los encabezados `X-User-*` y `X-User-Id` que mande el
cliente: la identidad que llega al servicio sale solo de los claims del token (tareas 2.1 y 2.2,
ver la sección "api-gateway"). El servicio mantiene la exigencia de `X-User-Id` y `X-User-Role` como
defensa en profundidad: no acepta que un cliente declare otro `clienteId` ni que un rol distinto de
`CLIENT` use los endpoints públicos.

| Endpoint | Sin identidad | ORGANIZER / ADMIN | CLIENT dueño | CLIENT con datos de otro |
|---|---|---|---|---|
| `GET /api/recommendations/preferences` | `401 UNAUTHORIZED` | `403 FORBIDDEN` | `200` | n/a: el `clienteId` sale de `X-User-Id` |
| `PUT /api/recommendations/preferences` | `401 UNAUTHORIZED` | `403 FORBIDDEN` | `200` | `403 NOT_PREFERENCE_OWNER` si el body trae otro `clienteId` |
| `GET /api/recommendations` | `401 UNAUTHORIZED` | `403 FORBIDDEN` | `200` | n/a: solo devuelve datos del `X-User-Id` |
| `GET /internal/preferences/{clienteId}` | `401 UNAUTHORIZED` sin `X-Internal-Token` | n/a: es interno | `200` con token interno | n/a: no se expone por el gateway |

Notas:

- Sin `X-User-Id` o `X-User-Role`, o con un UUID inválido, la respuesta es `401`.
- Un rol `ORGANIZER`, `ADMIN` o `ACCESS_STAFF` recibe `403` en los endpoints públicos de
  recommendation.
- El endpoint interno exige `X-Internal-Token`; no se publica por el gateway.
- El cuerpo de `PUT /preferences` no puede imponer un `clienteId` distinto del autenticado.

## Evidencia (16/11/2026)

```text
PreferenceControllerTest
  - GET sin identidad -> 401 UNAUTHORIZED
  - GET con ORGANIZER -> 403 FORBIDDEN
  - PUT sin identidad -> 401 UNAUTHORIZED
  - PUT con ORGANIZER -> 403 FORBIDDEN
  - PUT con UUID inválido -> 401 UNAUTHORIZED
  - GET/PUT del dueño -> 200
  - PUT con clienteId ajeno -> 403 NOT_PREFERENCE_OWNER
RecommendationControllerTest
  - GET sin identidad -> 401 UNAUTHORIZED
  - GET con ORGANIZER -> 403 FORBIDDEN
  - GET con UUID inválido -> 401 UNAUTHORIZED
  - GET del dueño -> 200
InternalPreferenceControllerTest
  - sin X-Internal-Token -> 401 UNAUTHORIZED
  - token incorrecto -> 401 UNAUTHORIZED
  - token correcto -> 200
```

Comando:

```bash
cd TP-TicketYa/recommendation-service
mvn -B -ntp -Dtest='PreferenceControllerTest,RecommendationControllerTest,InternalPreferenceControllerTest,OpenApiExportTest' test
```

El OpenAPI del servicio declara `bearerAuth`, `X-User-Id`, `X-User-Role` y
`X-Internal-Token`; se regenera con `OpenApiExportTest`.

## inventory-service y queue-service

El gateway deja pasar solo `CLIENT` a reservar, consultar y cancelar, y a entrar o consultar la fila.
El servicio repite esa regla. Los endpoints `/internal/**` de inventory exigen `X-Internal-Token`.
Abrir la fila ya lo exigía.

| Endpoint | Sin identidad | ORGANIZER | CLIENT | Sin token interno |
|---|---|---|---|---|
| `POST /api/inventory/reservations` | `401` | `403 FORBIDDEN` | sigue a la reserva | n/a |
| `GET /api/inventory/reservations/{id}` | `401` | `403 FORBIDDEN` | `200` si es el dueño | n/a |
| `DELETE /api/inventory/reservations/{id}` | `401` | `403 FORBIDDEN` | `204` si es el dueño y está vigente | n/a |
| `POST /api/queue/events/{id}/join` | `401` | `403 FORBIDDEN` | `201` o `200` | n/a |
| `GET /api/queue/events/{id}/position` | `401` | `403 FORBIDDEN` | `200` si está en la fila | n/a |
| `GET /internal/reservations/{id}` | n/a | n/a | n/a | `401` sin `X-Internal-Token` o con uno inválido; `200` con el token |

## orders-service (tareas 6.4 y 6.5)

Además del filtro del gateway, orders exige `X-User-Id` (UUID) y `X-User-Role` como defensa en
profundidad: sin ellos responde `401 UNAUTHORIZED` aunque la ruta sea pública en otro servicio.
Las rutas nuevas solo aceptan los roles listados; un `CLIENT` nunca puede validar QR ni consultar
reportes.

| Endpoint | Sin identidad | Rol no habilitado | Dueño / rol habilitado |
|---|---|---|---|
| `POST /api/orders/access/validate` | `401 UNAUTHORIZED` | `403 FORBIDDEN` (ej. `CLIENT`, `ORGANIZER`) | `200` con `ACCESS_STAFF` o `ADMIN` |
| `GET /api/orders/reports/events/{eventoId}` | `401 UNAUTHORIZED` | `403 FORBIDDEN` (ej. `CLIENT`, `ACCESS_STAFF`) | `200` con `ORGANIZER` dueño o `ADMIN` |
| `GET /api/orders/reports/events/{eventoId}` de otro organizador | `401 UNAUTHORIZED` | n/a | `403 NOT_EVENT_OWNER` |

Notas:

- El reporte autoriza comparando `X-User-Id` con el `organizadorId` de catalog (o con el snapshot
  `orden.evento_organizador_id` si catalog no responde). `ADMIN` pasa siempre.
- El servidor llama a catalog con `X-Internal-Token` (`INTERNAL_TOKEN`, default
  `ticketya-internal-dev`); esa cabecera la descarta el gateway y nunca debe venir del cliente.
- Si catalog cae se sirve la caché de 60 s o el snapshot local y se marca
  `datosDesactualizados: true`; sin dueño verificable responde `503 CATALOG_UNAVAILABLE`.
- El QR se consume con un `UPDATE` condicional, por lo que un segundo escaneo recibe
  `409 TICKET_ALREADY_USED` y se registra como `INGRESO_RECHAZADO`.
- Evidencia automática: `AccessControllerTest`, `AccessConcurrencyIntegrationTest`,
  `ReportControllerTest`, `ReportCalculationTest` y `ReportIntegrationTest`.

## api-gateway (tareas 2.1 y 2.2)

`POST /api/auth/login` lo atiende el propio gateway (no es una ruta proxy). Devuelve un JWT HS256
con `sub` (UUID), `role` y `email`. Los usuarios son **demo y en memoria** (4 roles, contraseña
`demo`). Es una simplificación respecto de `auth_db` y el ADR-016: no hay base de usuarios ni alta de cuentas.

Para el resto de las rutas, el filtro del gateway aplica estas reglas:

1. Siempre borra `X-User-Id`, `X-User-Role`, `X-User-Email` y `X-Internal-Token` que traiga el cliente.
2. Las lecturas públicas pasan sin token.
3. Cualquier otra ruta exige `Authorization: Bearer <jwt>`: sin token o con token inválido o vencido → `401`.
4. Si el rol del token no está habilitado para la ruta → `403`. Una ruta que no figura en la
   tabla de reglas también da `403` (default-deny).
5. Con token válido y rol habilitado, el gateway agrega `X-User-Id`, `X-User-Role` y `X-User-Email`
   tomados del JWT.
6. `/internal/**` no tiene ruta en el gateway: devuelve `404`.

| Endpoint | Sin token | Rol no habilitado | Rol habilitado |
|---|---|---|---|
| `POST /api/catalog/venues` y `/venues/{id}/sectors` | `401` | `403` | ADMIN |
| `POST/PUT /api/catalog/events`, `PUT .../prices/{sectorId}`, `POST .../publish` | `401` | `403` | ORGANIZER |
| `POST /api/catalog/events/{id}/cancel` | `401` | `403` | ADMIN |
| `GET /api/catalog/**` (lecturas) | pasa | n/a | público |
| `POST /api/orders`, `GET /api/orders/**`, devoluciones | `401` | `403` | autenticado |
| `POST /api/orders/returns/{id}/approve` y `/reject` | `401` | `403` | ADMIN |
| `POST /api/orders/access/validate` | `401` | `403` | ACCESS_STAFF o ADMIN |
| `GET /api/orders/reports/events/{eventoId}` | `401` | `403` | ORGANIZER o ADMIN |
| `GET /api/notifications` y `/{id}` | `401` | `403` | CLIENT |

Las filas de inventory, queue y recommendation están en las secciones de arriba.

## Evidencia del gateway (18/11/2026)

- Manual, con solo el gateway levantado: `docs/evidencia/entrega-2-p4-jwt.txt`
  (`401` sin token, token inválido y `X-User-*` sin token; login `401`/`400`/`200`;
  `403` con rol equivocado; `403` con `X-User-Role: ADMIN` falsificado; `404` en `/internal/**`).
- Automática: `mvn test` en `api-gateway`, 53 tests en verde (`JwtServiceTest`,
  `AuthControllerTest`, `IdentityHeaderFilterTest`, `GatewayRoutingTest`, `GatewayRevisionTest`,
  `HeaderPropagationTest`). `HeaderPropagationTest` comprueba con un servidor simulado que el servicio
  recibe los `X-User-*` del JWT y no los falsificados.

## Evidencia contra los servicios reales (20/11/2026)

Compose completo (15 contenedores `healthy`), todo por el gateway en `:8080`, con tokens reales del
login. Salida completa en `docs/evidencia/entrega-2-p4-seguridad-roles.txt`.

| Servicio | Endpoint | Sin token | Rol no habilitado | Rol habilitado |
|---|---|---|---|---|
| catalog | `GET /api/catalog/events` | `200` (público) | n/a | n/a |
| catalog | `POST /api/catalog/venues` | `401` | `403` (CLIENT, ORGANIZER) | `201` (ADMIN) |
| catalog | `POST /api/catalog/events/{id}/cancel` | n/a | `403` (CLIENT, ORGANIZER) | `404` (ADMIN, evento inexistente) |
| orders | `GET /api/orders` | `401` | `403` (ACCESS_STAFF) | `200` (CLIENT) |
| orders | `POST /api/orders/returns/{id}/approve` | n/a | `403` (CLIENT) | `404` (ADMIN, devolución inexistente) |
| orders | `POST /api/orders/access/validate` | n/a | `403` (CLIENT) | `404` (ACCESS_STAFF, QR inexistente) |
| orders | `GET /api/orders/reports/events/{id}` | n/a | `403` (CLIENT) | `404` (ORGANIZER, evento inexistente) |
| notification | `GET /api/notifications` | `401` | `403` (ORGANIZER) | `200` (CLIENT) |
| recommendation | `GET /api/recommendations` | `401` | `403` (ORGANIZER) | `200` (CLIENT) |

Un `404` con el rol habilitado y un recurso inexistente prueba que el filtro del gateway dejó pasar al
rol. El éxito con datos reales de acceso, devoluciones y reportes lo cubren los tests de cada servicio
(`AccessControllerTest`, `ReportControllerTest`) y la suite E2E.

## Pendiente

- Newman completo por rol (10.2): la colección `postman/TicketYa.entrega2.postman_collection.json` ya
  tiene Auth y Seguridad (22 tests en verde); falta que cada dueño complete su carpeta.
