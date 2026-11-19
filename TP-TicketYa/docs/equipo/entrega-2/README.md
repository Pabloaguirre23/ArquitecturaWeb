# Entrega 2 — Briefs del equipo

Esta carpeta tiene un brief por integrante para la Entrega 2. Cada uno puede clonar el repo,
leer **su** archivo y arrancar sin depender de una explicación en el chat.

| Persona | Nombre | Foco | Tareas OpenSpec | Brief |
|---|---|---|---|---|
| P1 | Iván | `catalog-service` + `notification-service` | 1.8, 2.4, 3.1–3.7, 8.1–8.4, 9.2 | [`p1-ivan.md`](p1-ivan.md) |
| P2 | Pablo | `inventory-service` + `queue-service` | 1.6, 2.5, 4.1–4.4, 5.1–5.5, 10.3 | [`p2-pablo.md`](p2-pablo.md) |
| P3 | Caro | `orders-service` | 2.6, 6.1–6.7, 9.3, 10.2, 10.5 | [`p3-caro.md`](p3-caro.md) |
| P4 | Tiago | `api-gateway` + infraestructura | 1.3–1.5, 2.1–2.3, 2.8, 9.1, 9.4–9.7, 10.8 | [`p4-tiago.md`](p4-tiago.md) |
| P5 | Franco | `recommendation-service` + calidad e integración | 1.1, 1.2, 1.7, 2.7, 7.1–7.5, 10.1, 10.4, 10.6, 10.7, 10.9 | [`p5-franco.md`](p5-franco.md) |

## Reglas comunes

1. **Repositorio:** `Pabloaguirre23/ArquitecturaWeb`, carpeta `TP-TicketYa/`, rama `main`
   protegida. El trabajo se mergea ahí; `Francogp131/TicketYa` es un espejo de trabajo.
2. **Rama por tarea:** `feat/<servicio>-<tema>` o `docs/<tema>`. Una tarea de `tasks.md` por PR
   (o dos si son del mismo alcance).
3. **No editar carpetas de otro servicio** sin acordarlo. Si un cambio de contrato afecta a
   otro, se avisa antes y se actualiza `docs/api-contract.md` en el mismo PR.
4. **Documentación en el mismo PR:** OpenAPI, README del servicio y contrato si cambia un
   endpoint. Los tests también van en el mismo PR.
5. **Un PR no se mergea sin al menos una aprobación** y CI verde.
6. **Cada tarea cerrada** se marca en `openspec/changes/entrega-2-completo/tasks.md` y deja
   evidencia en el cuerpo del PR.
7. **Commits:** `feat:`, `fix:`, `test:`, `docs:`, `chore:`; chicos y descriptivos.

## Lectura mínima antes de codificar

1. [`../../plan-entrega-2.md`](../../plan-entrega-2.md) — cronograma, carga y ruta crítica.
2. [`../../checklist-entrega-2.md`](../../checklist-entrega-2.md) — qué se verifica al final.
3. [`../../../openspec/changes/entrega-2-completo/proposal.md`](../../../openspec/changes/entrega-2-completo/proposal.md)
4. [`../../../openspec/changes/entrega-2-completo/design.md`](../../../openspec/changes/entrega-2-completo/design.md)
5. [`../../../openspec/changes/entrega-2-completo/tasks.md`](../../../openspec/changes/entrega-2-completo/tasks.md)
6. Los specs de su capacidad en `openspec/changes/entrega-2-completo/specs/`.

## Setup común

```bash
git clone git@github.com:Pabloaguirre23/ArquitecturaWeb.git
cd ArquitecturaWeb/TP-TicketYa
git switch main && git pull origin main

docker compose -f docker-compose.dev.yml up -d
# Redis, MongoDB y RabbitMQ se agregan en la tarea 1.4
```

Verificar Java 21 y Maven:

```bash
java -version
mvn -version
```

Si Maven usa otra versión:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```
