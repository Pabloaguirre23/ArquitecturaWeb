# Spec Delta

## ADDED Requirements

### Requirement: Indicador de alta demanda en búsqueda y detalle

El sistema SHALL incluir en los resultados de búsqueda y en el detalle del evento el
indicador de alta demanda. El estado de la fila se consulta en los endpoints de
queue-service (`/api/queue/events/{eventoId}/position`), no embebido en catalog.

#### Scenario: Resultado de alta demanda
- **WHEN** un cliente busca eventos y uno requiere fila virtual
- **THEN** el resultado incluye `altaDemanda: true`

#### Scenario: Detalle con alta demanda
- **WHEN** un cliente consulta el detalle de un evento de alta demanda
- **THEN** el detalle incluye `altaDemanda: true` y el cliente puede consultar su posición
  en queue-service por separado

#### Scenario: Evento sin fila
- **WHEN** un evento no requiere fila virtual
- **THEN** el indicador es `false`

### Requirement: Búsqueda resiliente

El sistema SHALL responder a la búsqueda con datos cacheados o con un error controlado cuando una dependencia no está disponible, sin devolver resultados inventados.

#### Scenario: Inventory caído en el detalle
- **WHEN** se consulta el detalle de un evento y inventory-service no responde
- **THEN** se responde 503 o se muestra la última disponibilidad cacheada marcada como desactualizada, según la configuración documentada

#### Scenario: Búsqueda sin cache
- **WHEN** Redis no está disponible
- **THEN** la búsqueda se resuelve contra la base y se responde 200

#### Scenario: Resultados vacíos
- **WHEN** no hay coincidencias
- **THEN** se responde 200 con una lista vacía y el total en cero
