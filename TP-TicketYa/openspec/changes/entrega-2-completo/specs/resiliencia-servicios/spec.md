# Spec Delta

## Purpose

Define el comportamiento del sistema ante fallos, demoras o indisponibilidad de servicios externos y dependencias internas, con timeouts, reintentos acotados, circuit breaker y fallbacks explícitos.

## ADDED Requirements

### Requirement: Timeouts en llamadas salientes

Toda llamada saliente a otro microservicio o a la pasarela de pagos SHALL tener un timeout de conexión y de lectura configurable.

#### Scenario: Dependencia lenta
- **WHEN** inventory-service tarda más que el timeout configurado en responder
- **THEN** orders-service corta la llamada y devuelve un error controlado sin dejar la orden `PAGADA`

#### Scenario: Timeout de la pasarela
- **WHEN** la pasarela de pagos no responde dentro del timeout
- **THEN** la orden queda en un estado reintentable y no se emiten entradas

#### Scenario: Timeout configurable
- **WHEN** se cambia el timeout por variable de entorno
- **THEN** el servicio usa el nuevo valor sin recompilar

### Requirement: Reintentos acotados

El sistema SHALL reintentar solo operaciones idempotentes, con backoff exponencial y jitter, y SHALL limitar la cantidad de intentos.

#### Scenario: Reintento de lectura
- **WHEN** catalog-service devuelve 503 en una consulta de lectura
- **THEN** el cliente reintenta hasta el máximo configurado y luego aplica el fallback

#### Scenario: Reintento de cobro
- **WHEN** la pasarela devuelve un error transitorio en un cobro con clave de idempotencia
- **THEN** el reintento no genera un segundo cobro

#### Scenario: Se agotan los reintentos
- **WHEN** se supera el máximo de intentos
- **THEN** se devuelve el error de negocio correspondiente y se registra la causa

### Requirement: Circuit breaker

El sistema SHALL abrir el circuito de una dependencia que acumula fallos, fallar rápido mientras esté abierto y probar la recuperación en estado semiabierto.

#### Scenario: Apertura del circuito
- **WHEN** una dependencia supera el umbral de fallos configurado
- **THEN** las llamadas siguientes fallan rápido sin esperar el timeout

#### Scenario: Recuperación
- **WHEN** el circuito está semiabierto y una llamada de prueba responde bien
- **THEN** el circuito se cierra y el tráfico vuelve a la normalidad

#### Scenario: Circuito abierto en compra
- **WHEN** el circuito de inventory-service está abierto durante una compra
- **THEN** orders-service no marca la orden como pagada y aplica la compensación correspondiente

### Requirement: Fallback por dependencia

El sistema SHALL definir un fallback explícito para cada dependencia crítica y SHALL documentar si falla abierto o cerrado.

#### Scenario: Catálogo no disponible en búsqueda
- **WHEN** catalog-service no responde y hay una copia cacheada vigente
- **THEN** la búsqueda devuelve la copia y marca los datos como posiblemente desactualizados

#### Scenario: Catálogo no disponible sin cache
- **WHEN** catalog-service no responde y no hay copia cacheada
- **THEN** se responde 503 con un error RFC 7807 y no se inventan resultados

#### Scenario: Notificaciones no disponibles
- **WHEN** notification-service está caído durante una compra
- **THEN** la compra se completa y la notificación queda pendiente para reintento

#### Scenario: Recomendaciones sin catálogo
- **WHEN** recommendation-service no puede consultar catalog-service
- **THEN** devuelve un fallback de eventos populares cacheados o una lista vacía, sin bloquear al cliente

### Requirement: Degradación controlada de la compra

El sistema SHALL priorizar la no doble venta y la coherencia de la orden por sobre la disponibilidad de funciones accesorias.

#### Scenario: Falla un servicio accesorio
- **WHEN** falla notification-service, recommendation-service o el envío de emails durante una compra
- **THEN** la compra sigue su curso normal y el fallo se registra como no bloqueante

#### Scenario: Falla un servicio crítico
- **WHEN** falla inventory-service o la pasarela de pagos durante una compra
- **THEN** la orden no queda pagada, se liberan los lugares y se devuelve un error controlado

### Requirement: Observabilidad de la resiliencia

El sistema SHALL exponer métricas y logs de timeouts, reintentos, apertura de circuitos y fallbacks.

#### Scenario: Métricas de circuit breaker
- **WHEN** un circuito cambia de estado
- **THEN** se incrementa una métrica y se registra un log con la dependencia y el motivo

#### Scenario: Trazabilidad del fallback
- **WHEN** se aplica un fallback
- **THEN** el log incluye la correlación de la solicitud y la causa del fallback
