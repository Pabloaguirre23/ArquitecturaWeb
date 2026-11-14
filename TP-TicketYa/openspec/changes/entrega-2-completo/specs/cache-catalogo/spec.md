# Spec Delta

## Purpose

Reduce la carga y la latencia del catálogo cacheando datos de lectura estables en Redis, con TTL e invalidación explícitos, sin cachear nunca la disponibilidad de asientos ni las reservas.

## ADDED Requirements

### Requirement: Datos de catálogo cacheados

El sistema SHALL cachear en Redis los lugares, el detalle estático de eventos y los resultados de búsqueda, usando claves que incluyan todos los parámetros de la consulta.

#### Scenario: Detalle de evento cacheado
- **WHEN** se consulta por primera vez el detalle estático de un evento publicado
- **THEN** la respuesta se guarda en cache con una clave que incluye el `eventoId`

#### Scenario: Búsqueda con parámetros
- **WHEN** se busca con tipo, ciudad, fechas, orden y página
- **THEN** la clave de cache incluye todos esos parámetros y no se mezclan resultados

#### Scenario: Cache hit
- **WHEN** se repite una consulta dentro del TTL
- **THEN** se responde desde Redis sin consultar la base y se indica el hit en las métricas

### Requirement: TTL por tipo de dato

El sistema SHALL definir un TTL configurable para cada tipo de dato cacheado y SHALL expirar las entradas automáticamente.

#### Scenario: TTL del detalle
- **WHEN** transcurre el TTL configurado para el detalle de un evento
- **THEN** la siguiente consulta vuelve a la base y renueva la entrada

#### Scenario: TTL de búsqueda
- **WHEN** transcurre el TTL configurado para una búsqueda
- **THEN** la siguiente búsqueda se recalcula y no se sirve una página vencida

#### Scenario: Configuración por entorno
- **WHEN** se cambian los TTL por variables de entorno
- **THEN** los servicios usan los nuevos valores sin recompilar

### Requirement: Invalidación de cache

El sistema SHALL invalidar las entradas afectadas cuando un evento se publica, se edita, se cancela, cambia de precio o cambia de estado.

#### Scenario: Publicación de evento
- **WHEN** un evento pasa de borrador a publicado
- **THEN** se invalidan el detalle y las búsquedas que podrían incluirlo

#### Scenario: Cambio de precio o fecha
- **WHEN** se modifica un precio o la fecha de un evento
- **THEN** se invalida su detalle y las búsquedas afectadas

#### Scenario: Cancelación
- **WHEN** un evento se cancela
- **THEN** se invalida su detalle y deja de aparecer en búsquedas cacheadas

### Requirement: Datos que nunca se cachean

El sistema SHALL excluir del cache la disponibilidad de asientos, el mapa de asientos, las reservas, las órdenes, la fila virtual y cualquier dato transaccional.

#### Scenario: Disponibilidad en tiempo real
- **WHEN** un cliente consulta la disponibilidad de un evento
- **THEN** la respuesta se obtiene de inventory-service y no de un cache de catálogo

#### Scenario: Mapa de asientos
- **WHEN** un cliente consulta los asientos de un evento
- **THEN** el mapa refleja el estado actual y no se sirve una copia cacheada

### Requirement: Degradación si Redis no está disponible

El sistema SHALL seguir funcionando cuando Redis no esté disponible, consultando la fuente de verdad y registrando la degradación.

#### Scenario: Redis caído en lectura
- **WHEN** Redis no responde al consultar el catálogo
- **THEN** la consulta se resuelve contra la base y se responde 200

#### Scenario: Redis caído al invalidar
- **WHEN** se publica o cancela un evento y Redis no responde
- **THEN** la operación de negocio se completa y la invalidación se registra para reintento o expira por TTL

### Requirement: Observabilidad del cache

El sistema SHALL exponer métricas de hits, misses, errores y latencia, y SHALL indicar en la respuesta si el dato provino del cache.

#### Scenario: Métricas de hit y miss
- **WHEN** se realizan consultas cacheadas
- **THEN** las métricas distinguen hits, misses y errores

#### Scenario: Cabecera de cache
- **WHEN** se responde una consulta de catálogo
- **THEN** la respuesta incluye `X-Cache: HIT`, `X-Cache: MISS` o `X-Cache: BYPASS`
