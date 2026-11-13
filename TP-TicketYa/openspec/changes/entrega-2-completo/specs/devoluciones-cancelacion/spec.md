# Spec Delta

## ADDED Requirements

### Requirement: Notificación de cancelación a compradores

El sistema SHALL notificar a todos los compradores de un evento cancelado y SHALL incluir el estado del reintegro.

#### Scenario: Cancelación con compradores
- **WHEN** se cancela un evento con entradas vendidas
- **THEN** cada comprador recibe una notificación de cancelación y el estado del reintegro

#### Scenario: Cancelación sin compradores
- **WHEN** se cancela un evento sin entradas vendidas
- **THEN** no se generan notificaciones y la cancelación se completa

#### Scenario: Falla de notificación
- **WHEN** notification-service no está disponible durante la cancelación
- **THEN** la cancelación y los reintegros se completan y las notificaciones quedan pendientes para reintento

### Requirement: Notificación de cambios de evento

El sistema SHALL notificar a los compradores cuando cambia la fecha o el lugar de un evento publicado.

#### Scenario: Cambio de fecha
- **WHEN** el organizador cambia la fecha de un evento con entradas vendidas
- **THEN** se notifica a cada comprador la fecha anterior y la nueva

#### Scenario: Cambio de lugar
- **WHEN** el organizador cambia el lugar de un evento con entradas vendidas
- **THEN** se notifica a cada comprador el lugar anterior y el nuevo

#### Scenario: Evento sin compradores
- **WHEN** se modifica un evento sin entradas vendidas
- **THEN** no se envían notificaciones

### Requirement: Devolución aprobada con reintegro idempotente

El sistema SHALL liberar el lugar, invalidar la entrada, solicitar el reintegro y publicar el evento de devolución de forma idempotente.

#### Scenario: Aprobación exitosa
- **WHEN** el administrador aprueba una devolución pendiente
- **THEN** inventory libera el lugar, la entrada pasa a `DEVUELTA`, se solicita el reintegro y se publica `devolucion.aprobada`

#### Scenario: Reintento de la aprobación
- **WHEN** se reintenta la aprobación de una devolución ya aprobada
- **THEN** no se libera dos veces el lugar ni se solicita un segundo reintegro

#### Scenario: Falla la liberación
- **WHEN** inventory-service no puede liberar el lugar
- **THEN** la devolución queda en `PROCESANDO`, no se marca aprobada y se reintenta

#### Scenario: Falla el reintegro
- **WHEN** la pasarela no confirma el reintegro
- **THEN** la devolución queda en un estado reintentable y la entrada no vuelve a estar válida

### Requirement: Reporte actualizado por devoluciones y cancelaciones

El sistema SHALL reflejar en los reportes las devoluciones aprobadas y las entradas reintegradas por cancelación.

#### Scenario: Devolución aprobada
- **WHEN** se aprueba una devolución
- **THEN** el reporte del evento descuenta la entrada vendida y el importe reintegrado

#### Scenario: Cancelación total
- **WHEN** se cancela un evento
- **THEN** el reporte muestra todas las entradas vendidas como reintegradas y la recaudación neta correspondiente
