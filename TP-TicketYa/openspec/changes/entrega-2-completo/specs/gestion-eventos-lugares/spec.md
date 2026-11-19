# Spec Delta

## ADDED Requirements

### Requirement: Evento de alta demanda

El sistema SHALL permitir configurar si un evento requiere fila virtual y SHALL incluir esa marca en su publicación, búsqueda y detalle.

#### Scenario: Marcar alta demanda
- **WHEN** un organizador configura su evento como de alta demanda antes de publicarlo
- **THEN** el evento guarda la marca y la conserva al publicarse

#### Scenario: Visible en búsqueda y detalle
- **WHEN** un cliente busca o consulta un evento de alta demanda
- **THEN** la respuesta incluye el indicador `altaDemanda` y la información de la fila cuando corresponde

#### Scenario: Cambio después de publicar
- **WHEN** se intenta cambiar la marca de alta demanda de un evento con ventas
- **THEN** se responde 409 y no se altera la configuración de la fila

### Requirement: Edición de evento publicado

El sistema SHALL permitir al organizador dueño editar la fecha, el lugar y la descripción de un evento publicado, validando que no existan ventas incompatibles y notificando el cambio.

#### Scenario: Edición válida
- **WHEN** el organizador dueño cambia la fecha o el lugar de un evento publicado
- **THEN** el evento se actualiza, se invalida su cache y se publica un evento `evento.actualizado`

#### Scenario: Evento ajeno
- **WHEN** un organizador intenta editar un evento que no le pertenece
- **THEN** se responde 403

#### Scenario: Evento finalizado o cancelado
- **WHEN** se intenta editar un evento finalizado o cancelado
- **THEN** se responde 409

#### Scenario: Fecha inválida
- **WHEN** se intenta cambiar la fecha a un valor pasado
- **THEN** se responde 400 y no se modifica el evento

### Requirement: Límite de entradas configurable

El sistema SHALL permitir al organizador definir el límite de entradas por cliente para su evento y SHALL exponerlo en el detalle y en el snapshot de inventario.

#### Scenario: Configurar límite
- **WHEN** el organizador dueño define un límite mayor a cero antes de publicar
- **THEN** el evento guarda el límite y lo usa inventory-service al reservar

#### Scenario: Límite inválido
- **WHEN** se define un límite menor o igual a cero
- **THEN** se responde 400

#### Scenario: Cambio con ventas
- **WHEN** se intenta bajar el límite por debajo de las entradas ya vendidas a un cliente
- **THEN** se responde 409 y no se aplica el cambio

### Requirement: Datos internos para reportes y recomendaciones

El sistema SHALL exponer endpoints internos de solo lectura con los datos de evento, organizador, sectores, capacidades, precios y participantes que necesitan reportes y recomendaciones.

#### Scenario: Datos de reporte
- **WHEN** orders-service consulta los datos de reporte de un evento
- **THEN** recibe organizador, sectores, capacidad y precios vigentes

#### Scenario: Candidatos de recomendación
- **WHEN** recommendation-service consulta eventos candidatos por ciudad, tipo, artista y rango de fechas
- **THEN** recibe solo eventos publicados o agotados, con los datos mínimos para rankear

#### Scenario: Acceso interno no autorizado
- **WHEN** se llama a estos endpoints sin el token interno
- **THEN** se responde 401 y no se exponen datos

### Requirement: Eventos de dominio del catálogo

El sistema SHALL publicar eventos `evento.publicado`, `evento.actualizado` y `evento.cancelado` mediante un outbox transaccional, para que notificaciones, recomendaciones y la fila virtual reaccionen sin acoplarse al catálogo.

#### Scenario: Publicación
- **WHEN** un evento pasa a `PUBLICADO`
- **THEN** se guarda y publica `evento.publicado` con los datos del evento, organizador, artistas y ciudad

#### Scenario: Actualización
- **WHEN** se modifica la fecha o el lugar de un evento publicado
- **THEN** se guarda y publica `evento.actualizado` con los datos anteriores y nuevos

#### Scenario: Cancelación
- **WHEN** un administrador cancela un evento
- **THEN** se guarda y publica `evento.cancelado` con el identificador y la cantidad de entradas vendidas

#### Scenario: Reintento de publicación
- **WHEN** falla la publicación a RabbitMQ
- **THEN** el evento queda en el outbox y se reintenta sin duplicar el cambio de estado
