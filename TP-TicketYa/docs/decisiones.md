# Decisiones de arquitectura (ADR) — Entregas 1 y 2

Registro corto de las decisiones que sostienen la Entrega 1 y la Entrega 2. Cada ADR indica
la decisión, las alternativas que se descartaron y las consecuencias que aceptamos. El
desarrollo técnico completo está en
[`../openspec/changes/entrega-1-core/design.md`](../openspec/changes/entrega-1-core/design.md)
y [`../openspec/changes/entrega-2-completo/design.md`](../openspec/changes/entrega-2-completo/design.md);
los diagramas en [`arquitectura.md`](arquitectura.md).

Las ADR-001 a ADR-013 son de la Entrega 1. Las ADR-014 a ADR-018 documentan las decisiones
transversales de la Entrega 2: patrones del enunciado, gateway reactivo, payload de
`orden.pagada` y sobre de eventos.

## ADR-001 — Microservicios por dominio con base propia

- **Decisión:** `catalog-service`, `inventory-service`, `orders-service` y `api-gateway`, cada uno con su base PostgreSQL y sin claves foráneas entre bases. La integración es REST por `/internal/**`.
- **Alternativas:** monolito modular (más simple, pero no cumple el requisito de microservicios); que catalog sea dueño de la disponibilidad (mezcla lectura de catálogo con escritura transaccional de asientos).
- **Consecuencias:** cada servicio se construye, testea y despliega por separado. Se acepta consistencia eventual y se resuelve con snapshots e idempotencia, no con transacciones distribuidas.

## ADR-002 — PostgreSQL 16 + Flyway, sin H2

- **Decisión:** PostgreSQL 16 para runtime y para los tests de integración/concurrencia con Testcontainers. Flyway versiona esquema y seeds.
- **Alternativas:** H2 en tests (descartado: su semántica de locks no representa a PostgreSQL); MySQL (válido, pero PostgreSQL ofrece `FOR UPDATE`, `RETURNING` y `ON CONFLICT`).
- **Consecuencias:** la prueba de no doble venta corre contra el motor real. Requiere Docker en local y en CI; los tests de Testcontainers se omiten si Docker no está disponible.

## ADR-003 — Gateway único e identidad por headers

- **Decisión:** todo el tráfico público entra por `api-gateway` (`/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`). El gateway exige y propaga `X-User-Id` y `X-User-Role`, y agrega `X-Correlation-Id`. Los `/internal/**` no se exponen.
- **Alternativas:** validar identidad en cada servicio (duplica lógica); implementar JWT en la Entrega 1 (fuera de alcance).
- **Consecuencias:** los servicios quedan simples y en la Entrega 2 solo cambia el gateway. Un header ausente o un rol inválido se resuelve con RFC 7807 (401/403) antes de llegar al servicio.

## ADR-004 — Contrato REST congelado y errores RFC 7807

- **Decisión:** [`api-contract.md`](api-contract.md) es la vista humana y el OpenAPI de cada servicio la fuente machine-readable. Todos los errores usan `application/problem+json` con `status`, `title`, `detail`, `instance`, `code` y `errors[]`.
- **Alternativas:** documentación por servicio sin contrato central; errores ad hoc por endpoint.
- **Consecuencias:** cualquier cambio de contrato se discute y actualiza en el mismo PR. Postman, los tests de controller y el gateway dependen de un formato estable.

## ADR-005 — Snapshot de publicación de catalog hacia inventory

- **Decisión:** al publicar, catalog envía a inventory un snapshot completo (evento, fecha, límite, sectores, precios y asientos) con clave idempotente `eventoId + version`. Inventory guarda el snapshot y no consulta catalog en el camino crítico de la reserva.
- **Alternativas:** que inventory consulte catalog en cada reserva (acopla la disponibilidad a la latencia y caída de catalog).
- **Consecuencias:** reservas rápidas y resistentes a fallas de catalog; el precio y la capacidad quedan congelados al publicar. Republicar es idempotente y no reinicia lo vendido.

## ADR-006 — La no doble venta la garantiza la base

- **Decisión:** asientos numerados con `SELECT ... FOR UPDATE` ordenado por ID dentro de una transacción; zonas con `UPDATE ... WHERE disponibles >= N RETURNING`; límite por cliente con `pg_advisory_xact_lock`; vencimiento y liberación con updates condicionales.
- **Alternativas:** locks en memoria o `synchronized` (no sirven con varias instancias); lock distribuido en Redis (infraestructura extra); optimistic locking (más reintentos).
- **Consecuencias:** con 100 hilos sobre el mismo asiento hay exactamente un ganador. Los deadlocks se mitigan tomando locks en orden y reintentando la transacción hasta 3 veces.

## ADR-007 — Orquestación de compra en orders con compensaciones

- **Decisión:** orders orquesta reserva → pago → confirmación en inventory → emisión de entradas. Si falla la confirmación o la emisión, deja la orden en `REINTEGRO_PENDIENTE` (nunca `PAGADA`), reintegra el pago y libera los lugares.
- **Alternativas:** transacción distribuida 2PC (sobredimensionada); coreografía con eventos (Entrega 2).
- **Consecuencias:** flujo síncrono simple y testeable, con estados intermedios trazables. La Saga/outbox formal queda como deuda para la Entrega 2.

## ADR-008 — Idempotencia de compra persistida

- **Decisión:** `POST /api/orders` exige `Idempotency-Key`. Orders guarda en `idempotency_key` el hash del cuerpo, el estado (`EN_PROCESO`/`COMPLETADA`) y la respuesta. Misma clave y mismo cuerpo devuelven la respuesta guardada; cuerpo distinto responde 422; clave en proceso responde 409 con `Retry-After`.
- **Alternativas:** deduplicar por `reservaId` (no cubre cambios de cuerpo); deduplicación en memoria (se pierde al reiniciar y no sirve multi-instancia).
- **Consecuencias:** los reintentos no generan otra orden ni otro cobro, incluso en concurrencia. La clave expira a las 24 h.

## ADR-009 — Pasarela de pagos mock detrás de una interfaz

- **Decisión:** `PaymentGateway` con una implementación mock que responde según el token (`APROBADO`, `RECHAZADO`, `LENTO`) y un `reintegrar` idempotente.
- **Alternativas:** integrar una pasarela real (fuera de alcance y con credenciales); lógica de pago embebida en `OrderService` (difícil de testear y reemplazar).
- **Consecuencias:** tests deterministas de aprobado, rechazado y timeout; en la Entrega 2 se cambia el adapter sin tocar la orquestación.

## ADR-010 — Devoluciones con ventana de 72 h y cancelación con reintento

- **Decisión:** la devolución se solicita hasta 72 h antes del evento, pasa por `PENDIENTE → PROCESANDO → APROBADA/RECHAZADA` y solo puede haber una abierta por entrada (índice único parcial). La cancelación de evento la inicia catalog y la ejecuta orders de forma idempotente; si orders falla, catalog guarda `cancelacion_pendiente` y un scheduler reintenta.
- **Alternativas:** que orders detecte la cancelación por polling (más lento y costoso); que catalog libere los lugares directamente (innecesario: un evento cancelado no se vuelve a vender).
- **Consecuencias:** una devolución aprobada libera el lugar, invalida la entrada y reintegra; un rechazo la deja `VALIDA`. La cancelación es eventualmente consistente y reintentable.

## ADR-011 — Documentación y verificación en el mismo PR

- **Decisión:** cada PR incluye código, tests, OpenAPI, README del servicio y la marca en `tasks.md`. `docs/README.md` es el índice y `docs/checklist-entrega-1.md` la lista verificable.
- **Alternativas:** documentar todo al final (el riesgo detectado en el plan: llegar al Día 9 con documentación atrasada).
- **Consecuencias:** los PRs son más grandes, pero el estado del proyecto siempre es verificable. OpenSpec (`entrega-1-core`) es la fuente de verdad del alcance.

## ADR-012 — “Suspender” en la Entrega 1 se resuelve como cancelar

- **Contexto:** el enunciado (ítem 3) y US-07 hablan de “suspender o cancelar”, pero el modelo
  de estados de la Entrega 1 no tiene `SUSPENDIDO`.
- **Decisión:** en la Entrega 1, “suspender” se implementa como **cancelación** (`CANCELADO`):
  el evento deja de venderse y las entradas vendidas pasan a reintegro. El estado `SUSPENDIDO`
  y la reactivación quedan para la Entrega 2.
- **Alternativas:** agregar `SUSPENDIDO` ahora (migración, transiciones, sincronización con
  inventory y orders) o no cubrir el caso (peor).
- **Consecuencias:** se cumple el bloqueo de ventas y el reintegro; no se puede reanudar un
  evento suspendido. La decisión se valida con la cátedra.

## ADR-013 — Consistencia de compra y deuda de saga/outbox

- **Contexto:** la compra involucra orders, inventory y la pasarela. La implementación de la
  Entrega 1 usa una transacción local con llamadas HTTP y compensaciones best-effort.
- **Decisión:** mantener la orquestación síncrona de la Entrega 1 y documentar la limitación:
  si la transacción queda rollback-only después de un fallo de emisión, la compensación puede
  no persistirse. La saga/outbox formal y la separación de transacciones se encaran en la
  Entrega 2.
- **Alternativas:** 2PC (sobredimensionado), coreografía con eventos (Entrega 2) o dejar el
  comportamiento implícito (peor).
- **Consecuencias:** el flujo feliz y los rechazos están cubiertos; los fallos extremos
  requieren un test de integración y seguimiento. No se debe declarar la consistencia como
  total hasta implementar la saga.

## ADR-014 — No se implementa un BFF

- **Contexto:** el enunciado menciona el patrón BFF y la Entrega 2 suma una UI web opcional.
- **Decisión:** no se crea un servicio BFF. `api-gateway` sigue siendo el punto único de
  entrada, valida JWT y aplica la matriz de roles; la UI consume los endpoints públicos. Si
  una pantalla necesita agregar datos, se resuelve con una llamada adicional o con un
  `GatewayFilter` de composición, no con un despliegue nuevo.
- **Alternativas:** BFF dedicado para la UI (duplica ruteo, seguridad y despliegue); GraphQL
  (fuera de alcance y sin equipo familiarizado); que la UI llame directo a cada servicio
  (rompe el punto único de entrada).
- **Consecuencias:** menos servicios que mantener y un solo lugar de autorización. La UI
  puede requerir más de una llamada por pantalla. Si en el futuro hay varios clientes con
  necesidades muy distintas, la decisión se revisa con una ADR nueva.

## ADR-015 — No se usa Service Discovery

- **Contexto:** los servicios se ejecutan con Docker Compose y el gateway conoce sus URLs por
  variables de entorno. El enunciado menciona Service Discovery.
- **Decisión:** no se agrega Eureka, Consul ni un registro propio. El DNS de Compose resuelve
  los nombres (`catalog-service`, `inventory-service`, etc.) y el gateway rutea con las URLs
  configuradas en `application.yml`/`.env`. No hay escalado dinámico en el alcance.
- **Alternativas:** Eureka/Consul (infraestructura y configuración extra para un entorno
  fijo); Spring Cloud LoadBalancer con lista estática (solo si se escala inventory);
  Kubernetes (fuera de alcance).
- **Consecuencias:** menos componentes y arranque reproducible. Para correr dos réplicas de
  un servicio hace falta un load balancer o un cliente que reparta; no se promete escalado
  transparente. Si se despliega en Kubernetes, el descubrimiento lo provee la plataforma.

## ADR-016 — El gateway WebFlux no bloquea el event loop al leer `auth_db`

- **Contexto:** `api-gateway` usa `spring-cloud-starter-gateway`, que es WebFlux, y la tarea
  2.1 le agrega login contra `auth_db`. Una consulta JDBC/JPA sin aislar bloquea el event loop.
- **Decisión:** no se agrega JPA al gateway. La opción preferida es **R2DBC** para la tabla
  `usuario` (`spring-boot-starter-data-r2dbc` + driver `r2dbc-postgresql`), manteniendo Flyway
  con JDBC solo en el arranque para migrar `auth_db`. Si el equipo prioriza minimizar
  dependencias, se permite `JdbcTemplate` dentro de
  `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, **solo en el login**.
  La validación del JWT es stateless y no consulta la base en cada request.
- **Alternativas:** gateway MVC + JPA (migración grande y el filtro actual ya es reactivo);
  JDBC bloqueante en el event loop (rechazado); un `auth-service` separado (suma un
  despliegue y una base para una tabla pequeña).
- **Consecuencias:** el camino reactivo queda sin bloqueos. R2DBC suma un driver y un
  repositorio reactivo; la variante `boundedElastic` exige disciplina para no repetirla en
  otros flujos. Flyway sigue usando JDBC en el arranque, lo cual es aceptable porque no corre
  en el event loop.

## ADR-017 — `orden.pagada` lleva el snapshot del evento

- **Contexto:** recommendation-service necesita `eventoTipo`, `artistas` y `ciudad` para el
  historial. `orders` solo guarda `eventoNombre` y `eventoFecha`, e `inventory` devuelve solo
  nombre y fecha en la reserva. Si el evento se publica con lo que hay hoy, el historial queda
  incompleto.
- **Decisión:** orders guarda el snapshot completo al crear la orden. catalog incluye
  `tipo`, `artistas` y `ciudad` en el snapshot de publicación hacia inventory; inventory los
  persiste y los devuelve en la reserva; orders los guarda en `orden` (migración 6.1:
  `evento_tipo`, `evento_artistas`, `evento_ciudad`) y los publica en `orden.pagada`. Si el
  equipo no extiende el snapshot de inventory, orders consulta el endpoint interno de catalog
  (3.5) al crear la orden y guarda el mismo snapshot; no se acepta que recommendation los
  busque después.
- **Alternativas:** que recommendation enriquezca el historial consultando catalog al
  consumir (si catalog está caído, el historial queda pobre); publicar solo IDs y enriquecer
  en la UI (peor); pedirle los datos al cliente (no confiable).
- **Consecuencias:** el evento es autosuficiente y el historial refleja el evento al momento
  de la compra. La migración 6.1 y el snapshot catalog→inventory→orders deben coordinarse
  entre P1, P2 y P3; Caro valida el contrato.

## ADR-018 — Sobre de eventos versionado y contrato compartido

- **Contexto:** la tarea 1.5 no estaba congelada y recommendation ya implementó un consumidor.
  Sin un contrato único, cada productor puede publicar un JSON distinto.
- **Decisión:** [`eventos.md`](eventos.md) define el sobre
  (`eventId`, `type`, `occurredAt`, `correlationId`, `version`, `payload`), las routing keys,
  las colas/DLQ y los payloads de `orden.pagada`, `orden.reintegrada`, `evento.*`,
  `devolucion.aprobada` y `entrada.utilizada`. El fixture
  [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json) se comparte con un test de
  contrato. Los cambios son aditivos; un cambio incompatible requiere `type` o `version`
  nueva. La entrega es al menos una vez y los consumidores deduplican por `eventId`.
- **Alternativas:** JSON distinto por servicio (deriva inevitable); Avro/Protobuf y un
  registry (sobredimensionado para el TP); no versionar (rompe consumidores en silencio).
- **Consecuencias:** P4 congela 1.5; P1/P3 implementan productores contra el mismo documento;
  P5 mantiene el test del fixture. Los errores van a DLQ y no se reencolan infinitamente.

## ADR-019 — Fila virtual con Redis y token de compra firmado

- **Contexto:** los eventos de alta demanda necesitan un orden de llegada justo, posición
  consultable y habilitación por tandas, con picos de miles de clientes haciendo polling. La
  reserva no puede depender de una llamada sincrónica a queue-service en el camino crítico.
- **Decisión:** queue-service usa Redis 7 como almacén primario (`waiting` como sorted set,
  `state` como hash, `seq` como contador y `admitted` para la tanda) y scripts Lua para que
  ingreso, posición y habilitación sean atómicos. La habilitación emite un JWT HS256 con
  `eventId`, `sub`, `jti`, `exp` y `type=QUEUE`, válido 5 minutos. Inventory valida firma,
  expiración, evento y cliente en local con la misma clave compartida y guarda el `jti` en la
  reserva; no consulta queue-service para reservar. La fila se abre y cierra por endpoints
  internos que catalog llama desde su outbox, con fallback si queue está caído.
- **Alternativas:** tabla SQL con `SELECT ... FOR UPDATE` (más contención y escrituras por
  cada polling); contador en memoria (no sirve con varias instancias); Redis Streams o
  WebSockets (más complejidad para un cliente que ya hace polling cada 2–5 segundos);
  validar el token contra queue en cada reserva (agrega un punto de fallo sincrónico).
- **Consecuencias:** la fila es eventualmente consistente con catalog: si queue está caído al
  publicar, el evento se publica igual y la apertura se reintenta en la próxima edición. Un
  token ya emitido sigue válido hasta su `exp` aunque la fila se cierre; inventory rechaza la
  reserva por el estado del evento. Redis necesita AOF y una política de reinicio documentada;
  si pierde los datos, la fila responde `409 QUEUE_NOT_OPEN` y no se emiten tokens nuevos.

## ADR-020 — Usuarios demo en memoria en el gateway

- **Contexto:** la Entrega 2 incorpora login JWT, roles `CLIENT`, `ORGANIZER`, `ADMIN` y
  `ACCESS_STAFF`, y necesita usuarios de prueba reproducibles para la demo, la suite E2E y la
  prueba de carga. Montar `auth_db` con Flyway, R2DBC y alta de cuentas agrega una base y un
  flujo que no son el objetivo de la entrega.
- **Decisión:** el gateway mantiene cuatro usuarios fijos en `DemoUserStore` (un `Map` en
  memoria), con la contraseña `demo` hasheada con BCrypt al arrancar. `POST /api/auth/login`
  valida con `BCryptPasswordEncoder.matches` y emite un JWT HS256 con `sub`, `role` y `email`.
  No hay registro, cambio de contraseña ni persistencia de usuarios.
- **Alternativas:** `auth_db` con `usuario`, Flyway y R2DBC (ADR-016; más realista, pero suma
  una base y un flujo de alta que no se evalúa en esta entrega); un proveedor de identidad
  externo (fuera de alcance); usuarios en texto plano (inaceptable incluso para demo).
- **Consecuencias:** al reiniciar el gateway los usuarios se recrean; no se pueden cambiar
  contraseñas ni agregar usuarios sin tocar código. El secreto JWT y los roles siguen siendo
  los reales del contrato, así que el resto de los servicios no distingue este origen. Para
  producción habría que reemplazar `DemoUserStore` por `auth_db` o un IdP, y rotar el secreto.

## ADR-021 — Secretos con default de desarrollo en el compose integral

- **Contexto:** el compose integral (`docker-compose.yml`) debe levantar el sistema completo con
  `docker compose up --build` en un clone limpio, sin pasos de configuración previos. El gateway,
  inventory y queue validan sus secretos al arrancar (fail-fast): `JWT_SECRET`,
  `QUEUE_TOKEN_SECRET` e `INTERNAL_TOKEN`. Si el compose no los inyecta, el arranque falla en un
  entorno recién clonado.
- **Decisión:** el compose integral interpola los secretos con default de desarrollo
  (`${JWT_SECRET:-ticketya-dev-secret-change-me-32-bytes-min!!}`,
  `${QUEUE_TOKEN_SECRET:-ticketya-queue-dev-secret-key-32b}`,
  `${INTERNAL_TOKEN:-ticketya-internal-dev}`) para que la demo y la corrección levanten sin `.env`.
  Son los mismos valores demo que documenta `.env.example`; no son secretos reales. El fail-fast
  de los servicios sigue activo cuando la variable no está definida (tests unitarios del gateway
  y de queue) y en cualquier despliegue que no use este compose.
- **Alternativas:** `${VAR:?}` en el compose (exige `.env` antes del primer `up` y suma fricción a
  la corrección); secretos en un vault (fuera del alcance de la materia); quitar los defaults y
  romper el clone limpio (rechazado).
- **Consecuencias:** el compose de la entrega **no es apto para producción**: hay que inyectar
  secretos reales por entorno y, para producción, reemplazar los `:-` por `:?`. Queda registrado
  como deuda aceptada de desarrollo, igual que los usuarios demo en memoria (ADR-020).
