# P5 — Recommendation + calidad e integración (Franco)

**Servicio:** `recommendation-service` (nuevo, MongoDB) + integración, evidencia y documentación.
**Tareas OpenSpec:** 1.1, 1.2, 1.7, 2.7, 7.1–7.5, 10.1, 10.4, 10.6, 10.7, 10.9.
**Día 1:** arrancás con **1.1** (acceso al repo), **1.2** (criterios US-25 a US-38) y **1.7**
(esqueleto de recommendation-service).

## 1. Antes de empezar

1. Leer [`../../plan-entrega-2.md`](../../plan-entrega-2.md) y el checklist completo.
2. Leer `openspec/changes/entrega-2-completo/specs/preferencias-recomendaciones/spec.md`,
   `notificaciones/spec.md`, `consistencia-saga/spec.md` y `observabilidad/spec.md`.
3. Leer `design.md`, decisiones 3, 6, 7, 10 y 13.
4. Verificar el repo del curso y el acceso:

```bash
git clone git@github.com:Pabloaguirre23/ArquitecturaWeb.git
cd ArquitecturaWeb/TP-TicketYa
git switch main && git pull origin main
```

Si no tenés permiso de escritura, pedíselo a Pablo antes de crear ramas.

## 2. Alcance

Sos dueño de:

- `recommendation-service/`: MongoDB, preferencias, historial, recomendaciones y similares.
- `integration-tests/` (o el módulo equivalente): pruebas entre servicios.
- `docs/informe-carga.md`, `docs/pruebas.md`, `docs/checklist-entrega-2.md`,
  `docs/modelo-datos.md`, `docs/decisiones.md`, `docs/README.md` y el cierre.
- Apoyo a todos en integración, evidencia y revisión de PRs.

**No edites código de un servicio sin acordarlo con su dueño.** Si encontrás un bug, abrí un PR
pequeño o coordiná con P1/P2/P3/P4.

## 3. Orden de trabajo

| Orden | Tarea | Qué hacés | Verificación |
|---|---|---|---|
| 1 | 1.1 | Verificar acceso al repo del curso, colaboradores, protección de `main` y plantilla de PR | Clone, push de prueba y PR de prueba |
| 2 | 1.2 | Completar criterios US-25 a US-38 y matriz de trazabilidad | Cada US con escenario y tarea |
| 3 | 1.7 | Esqueleto de recommendation-service con MongoDB, Actuator, Swagger y Dockerfile | Build, health y conexión |
| 4 | 7.1 | Colecciones, índices y seed de MongoDB | Índices y documento único por cliente |
| 5 | 7.2 | Preferencias `PUT`/`GET` con validación y ownership | 200, 400, 403 y upsert |
| 6 | 7.3 | Consumidor de `orden.pagada` idempotente | Duplicado no duplica historial |
| 7 | 7.4 | Recomendaciones por preferencias/co-compra con fallback y resiliencia | Con historial, nuevo, sin candidatos, catalog caído |
| 8 | 7.5 | Similares + OpenAPI/README | Orden por frecuencia y lista vacía |
| 9 | 2.7 | Seguridad JWT de recommendation | 401, 403 y 200 del dueño |
| 10 | 10.1 | Módulo de integración con Testcontainers/Compose y cinco flujos | Suite verde contra el compose |
| 11 | 10.4 | Informe de carga con 500/1000 usuarios | Percentiles, throughput, errores y análisis |
| 12 | 10.6 | Modelo de datos, ADR, contrato y READMEs nuevos | Enlaces y coherencia con migraciones |
| 13 | 10.7 | `docs/pruebas.md`, checklist 100 %, índice | Cada ítem con evidencia |
| 14 | 10.9 | Validación OpenSpec y cierre | `openspec validate --strict` y resumen |

## 4. Contratos que tenés que respetar

- **MongoDB:** colecciones `preferencias`, `historial_compras`, `eventos_vistos`; índices de la
  decisión 3 del `design.md`.
- **Eventos:** consumís `orden.pagada` con el sobre de P4; deduplicás por `eventId`.
- **Preferencias:** notification-service (P1) consulta tus preferencias por `/internal/**` con
  `X-Internal-Token` para artista favorito. Acordá el contrato antes de implementarlo.
- **Catalog:** para candidatos y datos de eventos usás el endpoint interno de P1 (tarea 3.5),
  con timeout/fallback.
- **Integración:** el módulo de tests usa el compose real; no mockees los servicios en los
  flujos de punta a punta.

## 5. Ramas sugeridas

| Rama | Tareas |
|---|---|
| `docs/equipo-entrega-2` | 1.1, 1.2 |
| `feat/recommendation-esqueleto` | 1.7 |
| `feat/recommendation-mongo` | 7.1 |
| `feat/recommendation-preferencias` | 7.2 |
| `feat/recommendation-historial` | 7.3 |
| `feat/recommendation-sugerencias` | 7.4 |
| `feat/recommendation-similares` | 7.5 |
| `feat/recommendation-seguridad` | 2.7 |
| `test/integracion-servicios` | 10.1 |
| `docs/informe-carga` | 10.4 |
| `docs/modelo-decisiones` | 10.6 |
| `docs/checklist-entrega-2` | 10.7 |
| `chore/cierre-entrega-2` | 10.9 |

## 6. Criterio de terminado

- [ ] Tareas marcadas en `tasks.md`.
- [ ] `mvn test` verde en recommendation.
- [ ] Suite de integración verde contra el compose.
- [ ] Informe de carga con resultados crudos.
- [ ] Checklist 100 % con evidencia.
- [ ] `openspec validate entrega-2-completo --strict` verde.
- [ ] PR aprobado por otro integrante.
- [ ] Sin secretos, `target/`, `.env` ni datos hardcodeados fuera del seed.
