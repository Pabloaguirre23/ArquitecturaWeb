# Spec Delta

## Purpose

Permite reconstruir qué pasó en cada solicitud y en cada operación crítica mediante logs estructurados, un identificador de correlación y una bitácora centralizada.

## ADDED Requirements

### Requirement: Log de solicitudes HTTP

El sistema SHALL registrar cada solicitud HTTP con método, ruta, usuario, rol, código de respuesta, duración y `X-Correlation-Id`.

#### Scenario: Solicitud exitosa
- **WHEN** un cliente llama a un endpoint a través del gateway
- **THEN** cada servicio involucrado registra una línea con los campos de la solicitud y la correlación

#### Scenario: Solicitud rechazada
- **WHEN** el gateway rechaza una solicitud por 401 o 403
- **THEN** el rechazo queda registrado con el motivo y la correlación

#### Scenario: Error inesperado
- **WHEN** un servicio responde 5xx
- **THEN** el log incluye la excepción, la correlación y el endpoint sin exponer datos sensibles

### Requirement: Correlación entre servicios

El gateway SHALL generar un `X-Correlation-Id` cuando el cliente no lo envíe, SHALL propagarlo a todos los servicios y llamadas internas y SHALL devolverlo en la respuesta.

#### Scenario: Sin correlación entrante
- **WHEN** una solicitud llega sin `X-Correlation-Id`
- **THEN** el gateway genera un UUID y lo propaga a los servicios

#### Scenario: Correlación entrante válida
- **WHEN** una solicitud llega con un `X-Correlation-Id` válido
- **THEN** el gateway conserva ese valor y lo devuelve en la respuesta

#### Scenario: Llamada interna
- **WHEN** un servicio llama a otro por `/internal/**`
- **THEN** envía el mismo `X-Correlation-Id` y el servicio destino lo registra

### Requirement: Bitácora de operaciones críticas

El sistema SHALL registrar en una bitácora persistente las operaciones de compra, devolución, cancelación, publicación de evento e ingreso con QR.

#### Scenario: Compra registrada
- **WHEN** una orden termina `PAGADA` o compensada
- **THEN** la bitácora registra la operación, el usuario, el resultado, el monto y la correlación

#### Scenario: Devolución registrada
- **WHEN** se solicita, aprueba o rechaza una devolución
- **THEN** la bitácora registra cada transición con su responsable

#### Scenario: Cancelación registrada
- **WHEN** se cancela un evento
- **THEN** la bitácora registra el evento, el administrador y la cantidad de entradas afectadas

#### Scenario: Ingreso registrado
- **WHEN** se valida un QR
- **THEN** la bitácora registra la entrada, el evento, el personal y el resultado

### Requirement: Log centralizado

El sistema SHALL emitir logs en formato JSON a la salida estándar y SHALL recolectarlos en un almacén central consultable por `X-Correlation-Id`, servicio y nivel.

#### Scenario: Consulta por correlación
- **WHEN** un operador busca una correlación en el log central
- **THEN** obtiene las líneas de todos los servicios que participaron en la operación

#### Scenario: Retención
- **WHEN** transcurre el período de retención configurado
- **THEN** los logs anteriores se descartan según la política documentada

#### Scenario: Servicio caído
- **WHEN** un servicio no puede escribir logs
- **THEN** la operación de negocio no falla por ese motivo y el error se registra localmente

### Requirement: Protección de datos en logs

El sistema SHALL evitar registrar contraseñas, tokens JWT, códigos de pago completos, cuerpos sensibles y datos personales innecesarios.

#### Scenario: Login
- **WHEN** se registra una solicitud de login
- **THEN** el log no incluye la contraseña ni el token emitido

#### Scenario: Notificación
- **WHEN** se registra el envío a un email
- **THEN** el email aparece enmascarado

#### Scenario: Pago
- **WHEN** se registra una operación con la pasarela
- **THEN** el log incluye el identificador externo y el estado, pero no datos completos del medio de pago

### Requirement: Evidencia de trazabilidad

El sistema SHALL permitir demostrar una operación crítica de punta a punta con la misma correlación en logs y bitácora.

#### Scenario: Compra de punta a punta
- **WHEN** se ejecuta una compra en el entorno de prueba
- **THEN** la evidencia muestra la correlación en gateway, orders, inventory, payment, notification y bitácora

#### Scenario: Ingreso de punta a punta
- **WHEN** se valida una entrada
- **THEN** la evidencia muestra la correlación en gateway, orders, bitácora y log central
