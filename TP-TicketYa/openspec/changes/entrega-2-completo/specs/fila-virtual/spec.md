# Spec Delta

## Purpose

Ordena el acceso a la compra de eventos masivos mediante una fila virtual con posición visible, habilitación por tandas y un token temporal que inventory-service exige para reservar.

## ADDED Requirements

### Requirement: Configuración de evento de alta demanda

El sistema SHALL permitir marcar un evento como de alta demanda y SHALL abrir su fila virtual al publicarse la venta.

#### Scenario: Evento marcado como alta demanda
- **WHEN** un organizador o administrador marca un evento como `altaDemanda` antes de publicarlo
- **THEN** el evento queda configurado para exigir fila virtual al abrirse la venta

#### Scenario: Evento sin alta demanda
- **WHEN** un evento no está marcado como de alta demanda
- **THEN** los clientes reservan sin pasar por la fila virtual

#### Scenario: Apertura de la fila
- **WHEN** se publica un evento de alta demanda
- **THEN** queue-service habilita la fila para ese evento y los clientes pueden ingresar

### Requirement: Ingreso a la fila virtual

El sistema SHALL permitir a un cliente autenticado ingresar a la fila de un evento de alta demanda y SHALL asignarle una posición respetando el orden de llegada.

#### Scenario: Primer ingreso
- **WHEN** un cliente ingresa a la fila de un evento de alta demanda con la venta abierta
- **THEN** recibe su posición, su estado `EN_ESPERA` y una estimación de espera

#### Scenario: Ingreso repetido
- **WHEN** el mismo cliente vuelve a ingresar a la misma fila
- **THEN** conserva su posición original y no se duplica su entrada

#### Scenario: Evento sin fila
- **WHEN** un cliente intenta ingresar a la fila de un evento que no es de alta demanda
- **THEN** se responde 409 indicando que la fila no aplica

#### Scenario: Venta cerrada
- **WHEN** un cliente intenta ingresar a la fila de un evento cancelado, finalizado o aún no publicado
- **THEN** se responde 409 y no se crea una entrada en la fila

### Requirement: Consulta de posición y estado

El sistema SHALL permitir al cliente consultar su posición actual, su estado en la fila y el token de compra cuando haya sido habilitado.

#### Scenario: Cliente en espera
- **WHEN** un cliente en la fila consulta su posición
- **THEN** recibe su posición actual, el estado `EN_ESPERA` y la cantidad de personas delante

#### Scenario: Cliente habilitado
- **WHEN** un cliente fue habilitado por una tanda
- **THEN** la consulta devuelve el estado `HABILITADO` y un token temporal de compra

#### Scenario: Cliente que no ingresó
- **WHEN** un cliente que nunca ingresó consulta la fila
- **THEN** se responde 404

### Requirement: Habilitación por tandas

El sistema SHALL habilitar a los clientes en orden de llegada, en tandas de tamaño configurable y a intervalos regulares, sin adelantar a quienes están detrás.

#### Scenario: Primera tanda
- **WHEN** la fila tiene más clientes que el tamaño de la tanda y se cumple el intervalo
- **THEN** los primeros N clientes pasan a `HABILITADO` y reciben su token

#### Scenario: Orden respetado
- **WHEN** un cliente ingresó después que otro
- **THEN** no puede ser habilitado antes que el cliente que lo precede

#### Scenario: Tanda sin clientes
- **WHEN** el intervalo se cumple y no hay clientes en espera
- **THEN** no se emiten tokens y la fila permanece vacía

### Requirement: Token temporal de compra

El sistema SHALL emitir un token firmado, asociado al evento y al cliente, con vencimiento corto, y inventory-service SHALL exigirlo para reservar en eventos de alta demanda.

#### Scenario: Reserva con token válido
- **WHEN** un cliente habilitado reserva lugares en el evento usando su token vigente
- **THEN** la reserva se procesa normalmente

#### Scenario: Token ausente
- **WHEN** un cliente intenta reservar en un evento de alta demanda sin token de fila
- **THEN** inventory-service responde 403 y no reserva ningún lugar

#### Scenario: Token vencido o de otro cliente
- **WHEN** se presenta un token vencido, de otro evento o emitido para otro cliente
- **THEN** inventory-service responde 403 y no reserva ningún lugar

#### Scenario: Token reutilizado dentro de la vigencia
- **WHEN** un cliente habilitado reintenta una reserva mientras su token sigue vigente
- **THEN** puede hacerlo sin volver a la fila, sujeto al límite de entradas por cliente

### Requirement: Cierre de la fila

El sistema SHALL cerrar la fila cuando el evento se cancela o finaliza y SHALL impedir nuevos ingresos y tokens nuevos.

#### Scenario: Evento cancelado
- **WHEN** se cancela un evento con fila abierta
- **THEN** la fila se cierra, no se admiten nuevos clientes y los tokens pendientes dejan de servir para reservar

#### Scenario: Evento agotado
- **WHEN** el inventario del evento se agota
- **THEN** la fila deja de habilitar clientes y los que quedan en espera reciben el estado `CERRADA`

### Requirement: Recuperación ante reinicio de Redis

El sistema SHALL reconstruir o conservar el estado de la fila ante un reinicio del almacenamiento, sin habilitar dos veces al mismo cliente.

#### Scenario: Reinicio con persistencia
- **WHEN** queue-service se reinicia y Redis conserva los datos
- **THEN** las posiciones y los estados de la fila se mantienen

#### Scenario: Pérdida de estado
- **WHEN** se pierde el estado de una fila
- **THEN** el sistema informa el cierre temporal de la fila y no emite tokens duplicados
