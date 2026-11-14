# Spec Delta

## ADDED Requirements

### Requirement: Indicador de alta demanda en búsqueda y detalle

El sistema SHALL incluir en los resultados de búsqueda y en el detalle del evento el indicador de alta demanda y, cuando corresponda, el estado de la fila virtual.

#### Scenario: Resultado de alta demanda
- **WHEN** un cliente busca eventos y uno requiere fila virtual
- **THEN** el resultado incluye `altaDemanda: true` y el estado de la fila si está abierta

#### Scenario: Detalle con fila
- **WHEN** un cliente consulta el detalle de un evento de alta demanda con la fila abierta
- **THEN** el detalle incluye la posición actual del cliente si ya ingresó y el enlace para ingresar si no lo hizo

#### Scenario: Evento sin fila
- **WHEN** un evento no requiere fila virtual
- **THEN** el indicador es `false` y no se muestra información de fila

### Requirement: Filtro por alta demanda

El sistema SHALL permitir filtrar la búsqueda por eventos que requieren fila virtual.

#### Scenario: Filtrar alta demanda
- **WHEN** un cliente busca con `altaDemanda=true`
- **THEN** recibe solo eventos marcados de alta demanda, además de los otros filtros aplicados

#### Scenario: Combinación con otros filtros
- **WHEN** se combina alta demanda con ciudad y tipo
- **THEN** se aplican todos los criterios con AND y se devuelve el total correcto

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
