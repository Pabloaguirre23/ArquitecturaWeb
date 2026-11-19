# Spec Delta

## Purpose

Permite validar entradas en el ingreso a un evento mediante el escaneo de su código QR, garantizando que cada entrada se use una sola vez y que el personal de acceso solo opere sobre el evento correcto.

## ADDED Requirements

### Requirement: Validación de una entrada en el ingreso

El sistema SHALL permitir al personal de acceso validar una entrada escaneada, verificando que exista, que corresponda al evento y que esté en estado `VALIDA`.

#### Scenario: Entrada válida
- **WHEN** el personal de acceso escanea el QR de una entrada `VALIDA` del evento
- **THEN** se responde 200 con los datos de la entrada y esta pasa a `UTILIZADA`

#### Scenario: Entrada inexistente
- **WHEN** se escanea un QR que no corresponde a ninguna entrada
- **THEN** se responde 404 y no se modifica ninguna entrada

#### Scenario: Entrada de otro evento
- **WHEN** se escanea una entrada válida que pertenece a otro evento
- **THEN** se responde 409 y la entrada no se marca como utilizada

### Requirement: Uso único del QR

El sistema SHALL impedir que una misma entrada se utilice más de una vez, incluso ante escaneos simultáneos.

#### Scenario: Segundo escaneo
- **WHEN** se vuelve a escanear una entrada ya `UTILIZADA`
- **THEN** se responde 409 indicando que la entrada ya fue utilizada

#### Scenario: Escaneos concurrentes
- **WHEN** dos dispositivos escanean el mismo QR al mismo tiempo
- **THEN** exactamente uno recibe 200 y el otro recibe 409, y la base registra una sola utilización

### Requirement: Entradas no habilitadas para el ingreso

El sistema SHALL rechazar el ingreso de entradas devueltas, en reintegro o de una orden que no esté paga.

#### Scenario: Entrada devuelta
- **WHEN** se escanea una entrada en estado `DEVUELTA`
- **THEN** se responde 409 y no se permite el ingreso

#### Scenario: Entrada en reintegro
- **WHEN** se escanea una entrada en estado `EN_REINTEGRO`
- **THEN** se responde 409 y no se permite el ingreso

### Requirement: Autorización del personal de acceso

El sistema SHALL permitir la validación de QR solo a usuarios con rol `ACCESS_STAFF` o `ADMIN`.

#### Scenario: Personal de acceso autorizado
- **WHEN** un usuario `ACCESS_STAFF` valida una entrada
- **THEN** la operación se permite y se registra su identidad

#### Scenario: Cliente no autorizado
- **WHEN** un usuario `CLIENT` intenta validar una entrada
- **THEN** se responde 403 y no se modifica la entrada

### Requirement: Registro de la validación

El sistema SHALL registrar en la bitácora cada validación de ingreso con la entrada, el evento, el usuario, el resultado y el identificador de correlación.

#### Scenario: Validación exitosa registrada
- **WHEN** una entrada se valida correctamente
- **THEN** la bitácora registra la operación `INGRESO_VALIDADO` con el personal, la entrada y la correlación

#### Scenario: Intento rechazado registrado
- **WHEN** se rechaza un ingreso por entrada ya utilizada o de otro evento
- **THEN** la bitácora registra el intento con su motivo y sin marcar la entrada como utilizada
