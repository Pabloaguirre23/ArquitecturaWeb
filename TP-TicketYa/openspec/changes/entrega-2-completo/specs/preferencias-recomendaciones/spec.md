# Spec Delta

## Purpose

Registra los gustos e historial de cada cliente y genera recomendaciones personalizadas de eventos, usando MongoDB por la flexibilidad de los documentos y la necesidad de agregaciones sobre historial.

## ADDED Requirements

### Requirement: Registro de preferencias

El sistema SHALL permitir a un cliente registrar y actualizar sus tipos de evento, artistas favoritos y ciudad de preferencia.

#### Scenario: Preferencias nuevas
- **WHEN** un cliente guarda sus tipos de evento, artistas y ciudad
- **THEN** las preferencias quedan persistidas y asociadas a su identificador

#### Scenario: Actualización
- **WHEN** un cliente vuelve a guardar preferencias con otros valores
- **THEN** se reemplazan los valores anteriores sin crear un documento duplicado

#### Scenario: Preferencias inválidas
- **WHEN** se envía un tipo de evento desconocido o una ciudad vacía
- **THEN** se responde 400 con el detalle del campo inválido

#### Scenario: Preferencias de otro cliente
- **WHEN** un cliente intenta modificar las preferencias de otro
- **THEN** se responde 403

### Requirement: Consulta de preferencias

El sistema SHALL permitir a un cliente consultar únicamente sus propias preferencias.

#### Scenario: Consulta propia
- **WHEN** un cliente autenticado consulta sus preferencias
- **THEN** recibe sus tipos, artistas, ciudad y fecha de actualización

#### Scenario: Cliente sin preferencias
- **WHEN** un cliente que nunca registró preferencias consulta el recurso
- **THEN** se responde 200 con preferencias vacías o valores por defecto documentados

### Requirement: Historial de compras para recomendaciones

El sistema SHALL construir un historial de los eventos comprados por cada cliente a partir de los eventos de compra pagada, de forma idempotente.

#### Scenario: Compra pagada
- **WHEN** se consume el evento de una orden pagada
- **THEN** se registra en el historial del cliente el evento, su tipo, artistas, ciudad y fecha

#### Scenario: Evento duplicado
- **WHEN** se vuelve a consumir el mismo evento de compra
- **THEN** no se duplica el registro del historial

#### Scenario: Compra anulada
- **WHEN** una orden se reintegra o cancela antes de emitir entradas
- **THEN** el historial no incorpora esa compra

### Requirement: Recomendaciones personalizadas

El sistema SHALL sugerir eventos publicados y futuros en función de las preferencias y el historial del cliente, indicando el motivo de cada sugerencia.

#### Scenario: Cliente con preferencias
- **WHEN** un cliente con preferencias consulta sus recomendaciones
- **THEN** recibe eventos de sus tipos, artistas o ciudad, ordenados por afinidad y con el motivo de la sugerencia

#### Scenario: Cliente sin historial
- **WHEN** un cliente sin compras ni preferencias consulta sus recomendaciones
- **THEN** recibe eventos populares de su ciudad o, si no hay ciudad, eventos populares generales

#### Scenario: Eventos no elegibles
- **WHEN** un evento está cancelado, finalizado o en borrador
- **THEN** no aparece en las recomendaciones

#### Scenario: Sin candidatos
- **WHEN** no hay eventos que cumplan los criterios
- **THEN** se responde 200 con una lista vacía

### Requirement: Recomendaciones colaborativas

El sistema SHALL calcular sugerencias del tipo “quienes compraron este evento también compraron” a partir de las compras históricas.

#### Scenario: Evento con compradores relacionados
- **WHEN** un cliente consulta eventos similares a uno que compró
- **THEN** recibe eventos comprados por otros clientes que compraron el mismo evento, ordenados por frecuencia

#### Scenario: Evento sin datos suficientes
- **WHEN** no hay suficiente historial para calcular similitud
- **THEN** se devuelve una lista vacía o un fallback basado en preferencias, sin error

### Requirement: Persistencia en MongoDB

El sistema SHALL persistir preferencias e historial en MongoDB con documentos independientes por cliente y SHALL definir índices para las consultas de recomendación.

#### Scenario: Documento de preferencias
- **WHEN** se consulta la colección de preferencias
- **THEN** cada cliente tiene como máximo un documento identificado por su `clienteId`

#### Scenario: Índices de historial
- **WHEN** se ejecuta una consulta de co-compra por evento
- **THEN** la consulta utiliza un índice sobre `eventoId` y `clienteId`

#### Scenario: Aislamiento de datos
- **WHEN** un cliente consulta sus recomendaciones o preferencias
- **THEN** el servicio solo lee documentos de su propio identificador
