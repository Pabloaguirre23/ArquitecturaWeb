# Seguridad — matriz de acceso

Este documento centraliza la matriz endpoint × rol y la evidencia de `401/403/200`.
La sección de `recommendation-service` corresponde a la tarea **2.7** (P5). Inventory y queue
corresponden a la tarea **2.5** (P2). Las filas de catalog, orders y notification las completa
P4 en la tarea 2.8.

## recommendation-service

El gateway **validará** el JWT y descartará los encabezados `X-User-*` que mande el cliente
(tareas 2.1–2.3, todavía pendientes al cierre de este documento). Mientras tanto, el servicio
exige `X-User-Id` y `X-User-Role` como defensa en profundidad: no acepta que un cliente declare
otro `clienteId` ni que un rol distinto de `CLIENT` use los endpoints públicos. La validación
del JWT en el borde no debe marcarse como hecha hasta que 2.1–2.3 estén integradas.

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

## Pendiente de P4 (tarea 2.8)

- Filas de catalog, orders y notification.
- Verificación de que el gateway elimina `X-User-*` y `X-Internal-Token` del tráfico externo.
- Matriz de roles en `docs/api-contract.md` y evidencia de `401/403/200` por endpoint.
