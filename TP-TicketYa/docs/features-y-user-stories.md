# TicketYa — Features y User Stories

Roles: **Cliente**, **Organizador**, **Administrador**, **Personal de acceso**, **Sistema**.
Entrega 1: el usuario se identifica por los encabezados `X-User-Id` y `X-User-Role` (sin JWT). Entrega 2: JWT con roles.
Los números `(n)` remiten a la sección FUNCIONALIDAD del enunciado. Criterios en formato Dado / Cuando / Entonces.

## Resumen de features

| Feature | Servicio | Entrega | Enunciado |
|---|---|---|---|
| F1 Lugares y sectores | catalog-service | 1 | 2 |
| F2 Eventos y publicación | catalog-service | 1 | 1, 3 |
| F3 Búsqueda de eventos | catalog-service | 1 | 9 |
| F4 Reserva temporal | inventory-service | 1 | 4 |
| F5 Compra y emisión de entradas | orders-service | 1 | 4 |
| F6 No doble venta e idempotencia | inventory-service + orders-service | 1 | 5 |
| F7 Devoluciones y cancelación | orders-service | 1 | 7 |
| F8 Fila virtual | queue-service (nuevo) | 2 | 6 |
| F9 Control de acceso con QR | orders-service | 2 | 8 |
| F10 Preferencias y recomendaciones | recommendation-service (nuevo, MongoDB) | 2 | 10 |
| F11 Notificaciones | notification-service (nuevo) | 2 | 11 |
| F12 Reportes para organizadores | orders-service | 2 | 12 |
| F13 Seguridad (JWT y roles) | api-gateway + todos | 2 | no funcional |
| F14 Trazabilidad y log centralizado | transversal | 2 | no funcional |

---

## F1 — Lugares y sectores (catalog-service) · Entrega 1

**US-01 Crear un lugar.** Como administrador quiero registrar un lugar con nombre, dirección y coordenadas para que los organizadores lo usen en sus eventos.
- Dado un administrador, cuando crea un lugar con nombre, dirección, latitud y longitud válidos, entonces el lugar se guarda con un ID y se responde 201.
- Dado un usuario que no es administrador, cuando intenta crear un lugar, entonces se responde 403.
- Dada una latitud fuera de [-90, 90] o una longitud fuera de [-180, 180], cuando se crea el lugar, entonces se responde 400.

**US-02 Definir sectores de un lugar.** Como administrador quiero agregar sectores numerados o no numerados a un lugar para modelar su distribución.
- Dado un lugar, cuando agrego un sector no numerado con capacidad máxima mayor a 0, entonces queda asociado al lugar.
- Dado un lugar, cuando agrego un sector numerado con filas y asientos por fila, entonces se generan los asientos identificados (fila + número).
- Dado un sector no numerado con capacidad 0 o negativa, cuando se crea, entonces se responde 400.

**US-03 Consultar lugares.** Como organizador quiero listar y ver el detalle de los lugares para elegir dónde hacer mi evento.
- Dado que existen lugares, cuando consulto el listado, entonces recibo nombre, dirección y ubicación de cada uno.
- Dado un ID inexistente, cuando consulto el detalle, entonces se responde 404.

## F2 — Eventos y publicación (catalog-service) · Entrega 1

**US-04 Crear un evento en borrador.** Como organizador quiero crear un evento con nombre, descripción, tipo, participantes, fecha y lugar para prepararlo antes de publicarlo.
- Dado un organizador, cuando crea un evento con datos válidos, entonces queda en estado `BORRADOR` y asociado a él.
- Dada una fecha en el pasado, cuando crea el evento, entonces se responde 400.
- Dado un tipo de evento no válido, cuando se crea, entonces se responde 400.

**US-05 Configurar precios por sector.** Como organizador quiero definir el precio de cada sector del lugar para mi evento.
- Dado un evento en borrador, cuando asigno un precio mayor o igual a 0 a un sector del lugar, entonces queda guardado para ese evento.
- Dado un sector que no pertenece al lugar del evento, cuando le asigno precio, entonces se responde 400.
- Dado un evento ya publicado, cuando intento cambiar un precio, entonces se responde 409.

**US-06 Publicar un evento.** Como organizador quiero publicar mi evento para que los clientes puedan comprar.
- Dado un evento en borrador con todos los sectores con precio, cuando lo publico, entonces pasa a `PUBLICADO` y se habilita su inventario en inventory-service.
- Dado un evento con algún sector sin precio, cuando intento publicarlo, entonces se responde 409 indicando qué falta.
- Dado un evento de otro organizador, cuando intento publicarlo, entonces se responde 403.

**US-07 Suspender o cancelar un evento.** Como administrador quiero suspender o cancelar cualquier evento.
- Dado un evento publicado, cuando el administrador lo cancela, entonces pasa a `CANCELADO` y se dispara el reintegro de las entradas vendidas (ver US-22).
- Dado un evento `FINALIZADO`, cuando se intenta cancelar, entonces se responde 409.
- Dado un organizador, cuando intenta cancelar un evento que no es suyo, entonces se responde 403.

**US-08 Estados del evento.** Como sistema quiero mantener el estado del evento coherente.
- Dado un evento publicado cuyo inventario se agota, entonces pasa a `AGOTADO`; si se libera un lugar (devolución), vuelve a `PUBLICADO`.
- Dado un evento publicado cuya fecha ya pasó, entonces pasa a `FINALIZADO`.
- Los estados válidos son borrador, publicado, agotado, cancelado y finalizado; cualquier otra transición se rechaza con 409.

## F3 — Búsqueda de eventos (catalog-service) · Entrega 1

**US-09 Buscar con múltiples criterios.** Como cliente quiero buscar eventos por tipo, organizador, participante, ciudad y rango de fechas.
- Dado un conjunto de criterios, cuando busco, entonces recibo solo eventos publicados o agotados que cumplen todos los criterios combinados.
- Dado que ningún evento coincide, cuando busco, entonces recibo una lista vacía con 200.
- Dado un rango de fechas con inicio posterior al fin, cuando busco, entonces se responde 400.

**US-10 Ordenar y paginar resultados.** Como cliente quiero ordenar por fecha, precio o relevancia y paginar.
- Dado un criterio de orden (fecha, precio mínimo o relevancia) y un tamaño de página, cuando busco, entonces los resultados vienen ordenados y paginados con el total.
- Dado un criterio de orden no soportado, cuando busco, entonces se responde 400.

**US-11 Ver el detalle de un evento.** Como cliente quiero ver el detalle de un evento con sectores, precios y disponibilidad.
- Dado un evento publicado, cuando consulto el detalle, entonces veo sus datos, sectores y precios.
- Dado un evento en borrador, cuando un cliente lo consulta, entonces se responde 404.

## F4 — Reserva temporal (inventory-service) · Entrega 1

**US-12 Reservar asientos numerados.** Como cliente quiero reservar asientos específicos mientras pago.
- Dado un asiento libre, cuando lo reservo, entonces queda `RESERVADO` para mí con vencimiento a los 10 minutos.
- Dado un asiento ya reservado o vendido, cuando intento reservarlo, entonces se responde 409.
- Dada una reserva de varios asientos, cuando alguno no está libre, entonces no se reserva ninguno (todo o nada).

**US-13 Reservar lugares en zona no numerada.** Como cliente quiero reservar N lugares de una zona con capacidad máxima.
- Dada una zona con disponibilidad suficiente, cuando reservo N lugares, entonces la disponibilidad baja en N.
- Dada una zona sin disponibilidad suficiente, cuando reservo, entonces se responde 409 y no cambia la disponibilidad.

**US-14 Límite de entradas por cliente.** Como organizador quiero limitar las entradas por cliente para un mismo evento.
- Dado un límite de 4 por cliente, cuando un cliente con 3 entradas intenta reservar 2 más, entonces se responde 422.
- El límite cuenta entradas vendidas y reservas vigentes del mismo cliente para el evento.

**US-15 Vencimiento y liberación.** Como sistema quiero liberar las reservas que no se pagan.
- Dada una reserva vencida, cuando pasa el tiempo límite, entonces los lugares vuelven a estar libres.
- Dada una reserva vencida, cuando el cliente intenta pagar, entonces se rechaza con 410 y no se emiten entradas.
- Dado un pago rechazado, entonces los lugares se liberan de inmediato.

## F5 — Compra y emisión de entradas (orders-service) · Entrega 1

**US-16 Iniciar una compra.** Como cliente quiero confirmar mi reserva y pagar para obtener mis entradas.
- Dada una reserva vigente, cuando inicio la compra, entonces se crea una orden `PENDIENTE_PAGO` con el detalle de ítems y el total.
- Dada una reserva inexistente o ajena, cuando inicio la compra, entonces se responde 404 o 403.

**US-17 Pago aprobado.** Como cliente quiero recibir mis entradas cuando el pago es aprobado.
- Dado un pago aprobado por la pasarela mock, entonces la orden pasa a `PAGADA`, los lugares quedan `VENDIDOS` y se emite una entrada por lugar con un código QR único.
- La orden registra el medio de pago y el detalle de la compra.

**US-18 Pago rechazado.** Como cliente quiero que, si mi pago falla, no se me cobre ni se pierda el lugar de otro.
- Dado un pago rechazado, entonces la orden pasa a `RECHAZADA`, no se emiten entradas y los lugares se liberan.
- Dada una falla de la reserva en un paso posterior, entonces se compensan los pasos ya realizados (ver F6).

**US-19 Ver mis entradas.** Como cliente quiero ver mis compras y mis entradas con su QR.
- Dado un cliente, cuando consulta sus entradas, entonces ve solo las suyas con evento, sector, asiento y QR.
- Dada una entrada de otro cliente, cuando la consulta, entonces se responde 403.

## F6 — No doble venta e idempotencia · Entrega 1

**US-20 Compra concurrente del mismo asiento.** Como sistema debo garantizar que un asiento no se vende dos veces.
- Dados 100 clientes que intentan reservar el mismo asiento a la vez, entonces exactamente uno tiene éxito y los demás reciben 409.
- Dado el último lugar de una zona y varios clientes simultáneos, entonces solo uno lo obtiene.
- El mecanismo funciona con varias instancias del servicio ejecutándose en paralelo.

**US-21 Reintento de pago idempotente.** Como cliente quiero que reintentar el pago por un problema de conexión no genere un cobro doble.
- Dada una solicitud de compra repetida con la misma clave de idempotencia (`Idempotency-Key`), entonces se devuelve el mismo resultado sin crear otra orden ni otro cobro.
- Dada la misma clave con un cuerpo distinto, entonces se responde 422.

## F7 — Devoluciones y cancelación (orders-service) · Entrega 1

**US-22 Solicitar devolución.** Como cliente quiero pedir la devolución de una entrada hasta 72 horas antes del evento.
- Dado un evento a más de 72 horas, cuando solicito la devolución de mi entrada válida, entonces se crea una solicitud `PENDIENTE`.
- Dado un evento a 72 horas o menos, cuando la solicito, entonces se responde 422.
- Dada una entrada ya usada o ya devuelta, cuando la solicito, entonces se responde 409.

**US-23 Resolver devolución.** Como administrador quiero aprobar o rechazar solicitudes de devolución.
- Dada una solicitud pendiente, cuando la apruebo, entonces el asiento o lugar se libera, la entrada se invalida y se solicita el reintegro a la pasarela.
- Dada una solicitud pendiente, cuando la rechazo, entonces la entrada sigue válida.
- Dada una solicitud ya resuelta, cuando intento resolverla de nuevo, entonces se responde 409.

**US-24 Cancelación de evento.** Como sistema debo reintegrar todas las entradas de un evento cancelado.
- Dado un evento cancelado, entonces todas sus entradas vendidas pasan a `EN_REINTEGRO` y se solicita el reintegro de cada una.
- Los compradores son notificados (Entrega 2, F11).


## F8 — Fila virtual (queue-service) · Entrega 2

**US-25 Entrar a la fila.** Como cliente quiero ingresar a una fila virtual cuando se abre la venta de un evento de alta demanda.
- Dado un evento marcado `altaDemanda` y con la venta abierta, cuando un cliente autenticado ingresa a la fila, entonces recibe 200 con su posición, estado `EN_ESPERA` y una estimación de espera.
- Dado un cliente que ya está en la fila, cuando vuelve a ingresar, entonces conserva su posición original y no se duplica su entrada.
- Dado un evento que no es de alta demanda, cuando un cliente intenta ingresar a la fila, entonces se responde 409 indicando que la fila no aplica.
- Dado un evento cancelado, finalizado o todavía en borrador, cuando un cliente intenta ingresar, entonces se responde 409 y no se crea una entrada en la fila.
- Dado un cliente sin autenticación, cuando intenta ingresar, entonces se responde 401.

**US-26 Ser habilitado por tandas.** Como cliente quiero ser habilitado en orden para comprar.
- Dada una fila con más clientes que el tamaño de tanda y cumplido el intervalo configurado, cuando se ejecuta la tanda, entonces los primeros N clientes pasan a `HABILITADO` y reciben un token temporal.
- Dado un cliente que ingresó después que otro, cuando se ejecutan las tandas, entonces no puede ser habilitado antes que el cliente que lo precede.
- Dado un token vigente, cuando el cliente reserva en el evento, entonces inventory-service acepta la reserva; si el token está vencido, ausente o es de otro evento/cliente, entonces responde 403.
- Dado un token vencido, cuando el cliente consulta su estado, entonces la fila lo vuelve a marcar `EN_ESPERA` o `EXPIRADO` según la configuración documentada.
- Dada una tanda sin clientes en espera, cuando se cumple el intervalo, entonces no se emiten tokens.

**US-27 Consultar mi posición.** Como cliente quiero ver cuánto falta.
- Dado un cliente en la fila, cuando consulta su posición, entonces recibe su posición actual, la cantidad de personas delante, su estado y la estimación de espera.
- Dado un cliente habilitado, cuando consulta su posición, entonces recibe el estado `HABILITADO` y su token de compra.
- Dado un cliente que nunca ingresó, cuando consulta la fila, entonces se responde 404.
- Dado un evento con la fila cerrada, cuando un cliente en espera consulta, entonces recibe el estado `CERRADA` y no se le emite token.

## F9 — Control de acceso con QR (orders-service) · Entrega 2

**US-28 Validar una entrada en el ingreso.** Como personal de acceso quiero escanear el QR para validar la entrada.
- Dada una entrada `VALIDA` del evento y sin usar, cuando el personal de acceso escanea su QR, entonces se responde 200, la entrada pasa a `UTILIZADA` y se registra el ingreso con el personal y la correlación.
- Dada una entrada ya `UTILIZADA`, cuando se vuelve a escanear, entonces se responde 409 y no se modifica la entrada.
- Dada una entrada de otro evento, cuando se escanea, entonces se responde 409 y no se permite el ingreso.
- Dado un QR inexistente, cuando se escanea, entonces se responde 404.
- Dada una entrada `DEVUELTA` o `EN_REINTEGRO`, cuando se escanea, entonces se responde 409.
- Dados dos escaneos simultáneos del mismo QR, cuando ambos llegan a la vez, entonces exactamente uno recibe 200 y el otro 409, y la base registra una sola utilización.
- Dado un usuario `CLIENT` u `ORGANIZER`, cuando intenta validar una entrada, entonces se responde 403; solo `ACCESS_STAFF` y `ADMIN` pueden hacerlo.

## F10 — Preferencias y recomendaciones (recommendation-service, MongoDB) · Entrega 2

**US-29 Registrar preferencias.** Como cliente quiero indicar tipos de evento, artistas favoritos y ciudad.
- Dado un cliente autenticado, cuando guarda tipos de evento, artistas y ciudad válidos, entonces las preferencias se persisten en MongoDB asociadas a su identificador.
- Dado un cliente que ya tenía preferencias, cuando guarda nuevos valores, entonces se reemplazan sin crear un documento duplicado.
- Dado un tipo de evento desconocido o una ciudad vacía, cuando guarda preferencias, entonces se responde 400 con el detalle del campo inválido.
- Dado un cliente, cuando intenta modificar las preferencias de otro, entonces se responde 403.
- Dado un cliente autenticado, cuando consulta sus preferencias, entonces recibe solo las suyas; si nunca registró, recibe 200 con valores vacíos o por defecto documentados.

**US-30 Recibir sugerencias.** Como cliente quiero ver eventos sugeridos según mi historial.
- Dado un cliente con historial de compras, cuando consulta sugerencias, entonces recibe eventos publicados y futuros de sus tipos, artistas o ciudad, ordenados por afinidad y con el motivo de cada sugerencia.
- Dado un cliente con historial, cuando consulta eventos similares a uno que compró, entonces recibe eventos comprados por otros clientes que compraron el mismo evento, ordenados por frecuencia.
- Dado un cliente sin historial ni preferencias, cuando consulta sugerencias, entonces recibe eventos populares de su ciudad o, si no tiene ciudad, populares generales.
- Dado un evento cancelado, finalizado o en borrador, cuando se calculan sugerencias, entonces no aparece en los resultados.
- Dado que no hay candidatos, cuando el cliente consulta, entonces se responde 200 con una lista vacía.
- Dado que catalog-service no responde, cuando el cliente consulta, entonces se devuelve el fallback cacheado o una lista vacía, sin bloquear al cliente.

## F11 — Notificaciones (notification-service) · Entrega 2

**US-31 Confirmación de compra.** Como cliente quiero recibir un aviso al confirmarse mi compra.
- Dada una orden que pasa a `PAGADA`, cuando se publica `orden.pagada`, entonces notification-service genera un aviso para el comprador con evento, sector, asientos, total y entradas.
- Dado que el envío simulado de email o push falla, cuando se procesa la notificación, entonces la compra sigue `PAGADA` y la notificación queda `PENDIENTE` para reintento.
- Dada una orden rechazada sin entradas, cuando se procesa, entonces no se envía una notificación de compra confirmada.
- Dado un evento `orden.pagada` duplicado, cuando se consume de nuevo, entonces se envía una sola notificación por destinatario y tipo.

**US-32 Cambios y cancelaciones.** Como cliente quiero enterarme si un evento cambia o se cancela.
- Dado un evento con compradores, cuando se cancela, entonces cada comprador recibe una notificación con el estado del reintegro.
- Dado un evento publicado con compradores, cuando cambia su fecha o lugar, entonces cada comprador recibe la fecha o el lugar anterior y el nuevo.
- Dado un evento sin compradores, cuando se cancela o modifica, entonces no se generan notificaciones.
- Dado que notification-service no está disponible durante la cancelación, cuando se completa la cancelación, entonces los reintegros se realizan y las notificaciones quedan pendientes para reintento.

**US-33 Artista favorito.** Como cliente quiero que me avisen cuando un artista favorito publica un evento en mi ciudad.
- Dado un evento publicado con un artista favorito en la ciudad preferida de un cliente, cuando se procesa `evento.publicado`, entonces se genera una notificación para ese cliente con el evento y su fecha.
- Dado un evento con el artista favorito pero en otra ciudad, cuando se procesa, entonces no se envía la notificación de artista favorito.
- Dado un cliente sin artistas o sin ciudad registrada, cuando se publica un evento, entonces no recibe notificaciones de artista favorito.

## F12 — Reportes (orders-service) · Entrega 2

**US-34 Reporte por evento.** Como organizador quiero consultar entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación.
- Dado un evento propio, cuando el organizador consulta el reporte, entonces recibe por sector las entradas vendidas, devueltas, ocupación y recaudación, y los totales del evento.
- Dado un evento sin ventas, cuando se consulta el reporte, entonces se devuelven ceros y ocupación 0 % sin error.
- Dado un evento de otro organizador, cuando se consulta el reporte, entonces se responde 403.
- Dado un evento inexistente, cuando se consulta, entonces se responde 404.
- Dado un administrador, cuando consulta el reporte de cualquier evento, entonces recibe el reporte completo.
- Dada una devolución aprobada, cuando se consulta el reporte, entonces se descuenta la entrada vendida y el importe reintegrado.
- Dado un evento cancelado, cuando se consulta el reporte, entonces las entradas vendidas aparecen como reintegradas y la recaudación es neta.

## F13 — Seguridad (api-gateway + servicios) · Entrega 2

**US-35 Autenticación con JWT.** Como usuario quiero autenticarme para usar el sistema.
- Dadas credenciales válidas, cuando inicio sesión, entonces recibo 200 con un JWT, mi rol, mi email y la expiración.
- Dadas credenciales inválidas o un email inexistente, cuando inicio sesión, entonces recibo 401 y no se emite token.
- Dados email o contraseña faltantes, cuando inicio sesión, entonces recibo 400 con los campos inválidos.
- Dado un token alterado, vencido o con firma inválida, cuando se usa en un endpoint protegido, entonces el gateway responde 401.
- Dado un token válido, cuando se decodifica, entonces contiene `sub`, `role`, `email` y `exp`, y no contiene la contraseña.

**US-36 Autorización por rol.** Como sistema debo restringir cada endpoint a sus roles.
- Dado un JWT con un rol no permitido, cuando accede a un endpoint, entonces se responde 403.
- Dado un request sin token, cuando accede a un endpoint protegido, entonces se responde 401.
- Dado un usuario `ACCESS_STAFF`, cuando valida un QR, entonces se permite; cuando intenta publicar un evento o ver reportes, entonces se responde 403.
- Dado un cliente que envía `X-User-Id`, `X-User-Role` o `X-User-Email` falsificados, cuando llama al gateway, entonces esos headers se descartan y se usan los del JWT.
- Dada una ruta sin regla explícita, cuando se invoca, entonces se deniega por defecto.
- Dado un request a `/internal/**` desde fuera del gateway, cuando intenta acceder, entonces se responde 404 o 403 y no llega al servicio.

## F14 — Trazabilidad (transversal) · Entrega 2

**US-37 Log de solicitudes HTTP.** Como equipo quiero registrar endpoint, usuario, código de respuesta y tiempo de cada solicitud.
- Dada una solicitud HTTP, cuando se procesa en cualquier servicio, entonces se registra en JSON el método, la ruta, el usuario, el rol, el estado, la duración y `X-Correlation-Id`.
- Dado un rechazo 401 o 403 del gateway, cuando ocurre, entonces queda registrado con el motivo y la correlación.
- Dado un error 5xx, cuando ocurre, entonces el log incluye la excepción y la correlación, sin datos sensibles.

**US-38 Log centralizado de operaciones críticas.** Como equipo quiero seguir una compra, devolución, cancelación o ingreso a través de los servicios con un identificador de correlación (`X-Correlation-Id`).
- Dada una operación crítica, cuando se ejecuta, entonces todos los servicios involucrados registran el mismo `X-Correlation-Id`.
- Dado un request sin correlación, cuando entra al gateway, entonces se genera un UUID y se propaga a los servicios y llamadas internas.
- Dada una compra, devolución, cancelación o ingreso, cuando termina, entonces queda en la bitácora con usuario, resultado, detalle y correlación.
- Dado un operador, cuando busca una correlación en el log central, entonces obtiene las líneas de todos los servicios que participaron.

## Requisitos no funcionales de la Entrega 2

Estos requisitos no son user stories, pero se verifican en el checklist y en las specs de arquitectura:

- **NoSQL:** MongoDB para preferencias, historial y recomendaciones, con justificación y colecciones documentadas.
- **Consistencia:** saga de compra, outbox transaccional, compensaciones y consumidores idempotentes; consistencia fuerte dentro de cada servicio y eventual entre servicios.
- **Resiliencia:** timeouts, reintentos acotados, circuit breaker y fallback en pasarela, inventory, catalog, queue y notification.
- **Cache:** Redis para catálogo con TTL e invalidación; disponibilidad, asientos, reservas y fila nunca se cachean.
- **Observabilidad:** logs JSON, correlación, bitácora y stack Loki/Promtail/Grafana.
- **Despliegue:** un Dockerfile por servicio y un `docker-compose.yml` integral con bases, Redis, MongoDB, RabbitMQ y observabilidad.
- **Testing:** integración entre servicios, matriz de seguridad por rol y prueba de carga con informe.

## Trazabilidad de la Entrega 2

| US | Capacidad | Spec | Tareas |
|---|---|---|---|
| US-25 | `fila-virtual` | `fila-virtual/spec.md` | 4.1, 4.3 |
| US-26 | `fila-virtual` | `fila-virtual/spec.md` | 4.2, 5.1 |
| US-27 | `fila-virtual` | `fila-virtual/spec.md` | 4.1, 4.4 |
| US-28 | `control-acceso-qr` | `control-acceso-qr/spec.md` | 6.4 |
| US-29 | `preferencias-recomendaciones` | `preferencias-recomendaciones/spec.md` | 7.1, 7.2 |
| US-30 | `preferencias-recomendaciones` | `preferencias-recomendaciones/spec.md` | 7.3, 7.4, 7.5 |
| US-31 | `notificaciones` | `notificaciones/spec.md` | 8.1, 8.2 |
| US-32 | `notificaciones` | `notificaciones/spec.md` | 8.3 |
| US-33 | `notificaciones` | `notificaciones/spec.md` | 8.3 |
| US-34 | `reportes-organizador` | `reportes-organizador/spec.md` | 6.5 |
| US-35 | `seguridad-jwt` | `seguridad-jwt/spec.md` | 2.1, 2.2 |
| US-36 | `seguridad-jwt` | `seguridad-jwt/spec.md` | 2.2, 2.4–2.8 |
| US-37 | `observabilidad` | `observabilidad/spec.md` | 9.1 |
| US-38 | `observabilidad` | `observabilidad/spec.md` | 9.2–9.4 |

Los criterios de US-01 a US-24 siguen vigentes; los cambios de la Entrega 2 que los afectan
(JWT, token de fila, notificaciones y reportes) están cubiertos por las tareas 2.1–2.8, 5.1,
8.3 y 6.5.
