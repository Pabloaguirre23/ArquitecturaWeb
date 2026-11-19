# Spec Delta

## ADDED Requirements

### Requirement: Reserva en eventos de alta demanda

El sistema SHALL exigir un token de fila virtual vigente para reservar lugares en un evento de alta demanda y SHALL validarlo contra el evento y el cliente.

#### Scenario: Token válido
- **WHEN** un cliente habilitado reserva lugares con un token vigente del evento
- **THEN** la reserva se crea con las mismas reglas de atomicidad y límite que un evento normal

#### Scenario: Token ausente o inválido
- **WHEN** se reserva en un evento de alta demanda sin token, con token vencido o con token de otro evento o cliente
- **THEN** se responde 403 y no se modifica ningún asiento ni cupo

#### Scenario: Evento normal
- **WHEN** se reserva en un evento que no es de alta demanda
- **THEN** no se exige token de fila

#### Scenario: Token de fila sin reserva
- **WHEN** un cliente habilitado no completa la reserva antes del vencimiento del token
- **THEN** el token deja de ser válido y el cliente debe volver a la fila si aún hay lugar

### Requirement: Cancelación de reserva por el cliente

El sistema SHALL permitir al cliente cancelar su propia reserva vigente y SHALL liberar los lugares de forma idempotente.

#### Scenario: Cancelación exitosa
- **WHEN** el cliente dueño cancela una reserva vigente
- **THEN** los asientos o el cupo se liberan y la reserva queda `CANCELADA`

#### Scenario: Cancelación repetida
- **WHEN** se repite la cancelación de la misma reserva
- **THEN** se responde de forma idempotente sin liberar dos veces ni afectar otras reservas

#### Scenario: Reserva ajena
- **WHEN** un cliente intenta cancelar una reserva de otro
- **THEN** se responde 403

#### Scenario: Reserva ya vencida o confirmada
- **WHEN** se intenta cancelar una reserva vencida o ya confirmada
- **THEN** se responde 409 o 410 según el estado, sin modificar la venta

### Requirement: Email de contacto del comprador

El sistema SHALL registrar el email del comprador autenticado en la orden y SHALL incluirlo en los eventos de dominio para que notification-service pueda enviar los avisos.

#### Scenario: Compra con email
- **WHEN** un cliente con email en su identidad crea una orden
- **THEN** la orden guarda el email y el evento `orden.pagada` lo incluye

#### Scenario: Cliente sin email
- **WHEN** la identidad no tiene email
- **THEN** la compra se completa y la orden queda marcada para notificación fallida, sin inventar un destinatario

#### Scenario: Cambio de email
- **WHEN** el cliente cambia su email después de comprar
- **THEN** la orden conserva el email usado al momento de la compra

### Requirement: Publicación de eventos de compra

El sistema SHALL publicar `orden.pagada`, `orden.reintegrada` y `devolucion.aprobada` a través del outbox, en la misma transacción que el cambio de estado.

#### Scenario: Orden pagada
- **WHEN** una orden termina `PAGADA`
- **THEN** se publica `orden.pagada` con cliente, email, evento, ítems, total y entradas

#### Scenario: Reintegro
- **WHEN** se completa un reintegro por devolución o cancelación
- **THEN** se publica `orden.reintegrada` con el monto y el motivo

#### Scenario: Devolución aprobada
- **WHEN** se aprueba una devolución
- **THEN** se publica `devolucion.aprobada` con la entrada, el cliente y el evento

### Requirement: Compra tolerante a fallos de servicios accesorios

El sistema SHALL completar la compra aunque fallen notification-service o recommendation-service, y SHALL registrar el fallo sin revertir la venta.

#### Scenario: Notificación caída
- **WHEN** notification-service no está disponible al confirmarse la compra
- **THEN** la orden queda `PAGADA`, las entradas se emiten y el evento queda pendiente en el outbox

#### Scenario: Recomendación caída
- **WHEN** recommendation-service no puede registrar el historial en el momento
- **THEN** la compra se completa y el evento se reintenta o se reconcilia después

#### Scenario: Falla crítica
- **WHEN** falla inventory-service o la pasarela de pagos
- **THEN** la orden no queda `PAGADA` y se aplican las compensaciones de la saga
