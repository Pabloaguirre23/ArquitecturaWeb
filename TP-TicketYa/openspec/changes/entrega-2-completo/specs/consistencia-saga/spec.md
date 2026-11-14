# Spec Delta

## Purpose

Define cómo la compra de entradas mantiene la coherencia entre servicios mediante una saga con compensaciones, un outbox transaccional y consumidores idempotentes, evitando estados imposibles como una orden pagada sin entradas.

## ADDED Requirements

### Requirement: Saga de compra

El sistema SHALL ejecutar la compra como una saga orquestada con pasos explícitos, estados persistidos y comandos idempotentes entre inventory-service y orders-service.

#### Scenario: Compra exitosa
- **WHEN** un cliente paga una reserva vigente y todas las dependencias responden
- **THEN** la secuencia reserva, pago, confirmación de inventario y emisión de entradas termina con la orden `PAGADA` y las entradas `VALIDA`

#### Scenario: Reintento de un paso
- **WHEN** un paso de la saga se reintenta con la misma clave de operación
- **THEN** no se duplica la reserva, el cobro, la confirmación ni la entrada

#### Scenario: Estado de la saga consultable
- **WHEN** un operador consulta una orden
- **THEN** puede ver el estado actual de la saga y el último paso completado

### Requirement: Compensaciones ante fallos parciales

El sistema SHALL deshacer los pasos ya realizados cuando un paso posterior falla, y SHALL dejar registro del resultado de cada compensación.

#### Scenario: Falla la confirmación de inventario después del pago
- **WHEN** la pasarela aprueba el pago pero inventory-service no confirma la reserva
- **THEN** orders-service solicita el reintegro, libera la reserva y deja la orden en `ERROR_PAGO` o `REINTEGRO_PENDIENTE`, nunca `PAGADA`

#### Scenario: Falla la emisión de entradas
- **WHEN** falla la emisión de entradas después de confirmar la reserva
- **THEN** se liberan los lugares, se solicita el reintegro y no queda ninguna entrada válida

#### Scenario: Falla la compensación
- **WHEN** el reintegro o la liberación falla durante la compensación
- **THEN** la orden queda en un estado reintentable y un proceso programado vuelve a intentar la compensación

### Requirement: Outbox transaccional

El sistema SHALL escribir los eventos de dominio en una tabla outbox dentro de la misma transacción que el cambio de negocio y SHALL publicarlos de forma asíncrona con al menos una entrega.

#### Scenario: Evento y cambio en la misma transacción
- **WHEN** orders-service marca una orden como `PAGADA`
- **THEN** el evento `orden.pagada` se guarda en el outbox en la misma transacción

#### Scenario: Publicación fallida
- **WHEN** RabbitMQ no está disponible al intentar publicar un evento del outbox
- **THEN** el evento queda pendiente y se reintenta sin perderlo ni duplicar el cambio de negocio

#### Scenario: Publicación repetida
- **WHEN** el publicador reintenta un evento ya publicado
- **THEN** el consumidor lo reconoce por su `eventId` y no duplica el efecto

### Requirement: Consumidores idempotentes

Cada consumidor de eventos SHALL registrar los identificadores procesados y SHALL ignorar eventos duplicados.

#### Scenario: Evento duplicado
- **WHEN** recommendation-service recibe dos veces el mismo `orden.pagada`
- **THEN** el historial del cliente registra una sola compra

#### Scenario: Reentrega después de un reinicio
- **WHEN** un consumidor se reinicia y RabbitMQ reentrega un evento ya procesado
- **THEN** el consumidor no vuelve a notificar ni a registrar el historial

#### Scenario: Evento fuera de orden
- **WHEN** llega un evento de cancelación antes que el de publicación
- **THEN** el consumidor aplica una regla documentada y no deja el dato en un estado inválido

### Requirement: Contrato de eventos de dominio

El sistema SHALL publicar eventos con un sobre común que incluya `eventId`, `type`, `occurredAt`, `correlationId`, `version` y `payload`.

#### Scenario: Sobre completo
- **WHEN** se publica cualquier evento de dominio
- **THEN** el mensaje contiene los campos del sobre y un `eventId` único

#### Scenario: Versión incompatible
- **WHEN** un consumidor recibe una versión de evento que no soporta
- **THEN** lo deriva a la DLQ y registra el error sin bloquear la cola principal

### Requirement: Cola de mensajes muertos y reconciliación

El sistema SHALL derivar a una DLQ los mensajes que no puedan procesarse y SHALL ofrecer un mecanismo de reconciliación para los estados intermedios.

#### Scenario: Mensaje venenoso
- **WHEN** un evento falla repetidamente por un error de formato
- **THEN** se envía a la DLQ y se registra para revisión

#### Scenario: Reconciliación de compra
- **WHEN** una orden queda en un estado intermedio después de un reinicio
- **THEN** un proceso programado verifica inventory y la pasarela y completa o compensa la saga

### Requirement: Consistencia documentada

El sistema SHALL documentar qué operaciones son fuertemente consistentes dentro de un servicio y cuáles son eventualmente consistentes entre servicios.

#### Scenario: Lectura de disponibilidad
- **WHEN** un cliente consulta la disponibilidad después de una compra
- **THEN** inventory-service refleja el cambio de forma fuerte e inmediata

#### Scenario: Lectura de recomendaciones
- **WHEN** un cliente consulta recomendaciones inmediatamente después de comprar
- **THEN** el nuevo historial puede aparecer con un retardo acotado, sin afectar la compra
