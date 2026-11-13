# Spec Delta

## Purpose

Envía avisos a los clientes ante compras, cancelaciones, cambios de evento y publicaciones de artistas favoritos, de forma asíncrona y sin bloquear las operaciones principales.

## ADDED Requirements

### Requirement: Notificación de compra confirmada

El sistema SHALL enviar un aviso al comprador cuando una orden queda pagada, con el detalle de la compra y sus entradas.

#### Scenario: Compra pagada
- **WHEN** orders-service publica el evento de una orden pagada
- **THEN** notification-service genera una notificación para el comprador con evento, sector, asientos, total y código QR

#### Scenario: Falla del canal de envío
- **WHEN** el envío simulado de email o push falla
- **THEN** la compra sigue `PAGADA` y la notificación queda pendiente para reintento

#### Scenario: Compra rechazada
- **WHEN** una orden es rechazada y no se emiten entradas
- **THEN** no se envía una notificación de compra confirmada

### Requirement: Notificación por cancelación o cambio de evento

El sistema SHALL notificar a todos los compradores cuando un evento se cancela o cambia su fecha o lugar.

#### Scenario: Evento cancelado
- **WHEN** catalog-service publica la cancelación de un evento con entradas vendidas
- **THEN** se notifica a cada comprador que su entrada será reintegrada

#### Scenario: Cambio de fecha o lugar
- **WHEN** un organizador modifica la fecha o el lugar de un evento publicado
- **THEN** se notifica a los compradores el nuevo dato y el evento conserva su estado

#### Scenario: Evento sin compradores
- **WHEN** se cancela o modifica un evento sin entradas vendidas
- **THEN** no se generan notificaciones

### Requirement: Notificación de artista favorito

El sistema SHALL notificar a los clientes que siguen a un artista cuando se publica un evento suyo en su ciudad de preferencia.

#### Scenario: Coincidencia de artista y ciudad
- **WHEN** se publica un evento con un artista favorito en la ciudad preferida de un cliente
- **THEN** se genera una notificación para ese cliente con el evento y su fecha

#### Scenario: Coincidencia parcial
- **WHEN** el artista es favorito pero el evento es en otra ciudad
- **THEN** no se envía la notificación de artista favorito

#### Scenario: Cliente sin preferencias
- **WHEN** un cliente no registró artistas ni ciudad
- **THEN** no recibe notificaciones de artista favorito

### Requirement: Consulta de notificaciones propias

El sistema SHALL permitir a un cliente consultar sus notificaciones y su estado de envío.

#### Scenario: Listado propio
- **WHEN** un cliente consulta sus notificaciones
- **THEN** recibe solo las suyas, ordenadas por fecha descendente

#### Scenario: Notificación ajena
- **WHEN** un cliente intenta consultar una notificación de otro
- **THEN** se responde 403

#### Scenario: Estados de notificación
- **WHEN** se consulta una notificación
- **THEN** su estado es `PENDIENTE`, `ENVIADA` o `FALLIDA` y se informa la cantidad de intentos

### Requirement: Entrega asíncrona con reintentos

El sistema SHALL consumir los eventos de dominio de forma asíncrona, reintentar los envíos fallidos con backoff y derivar a una cola de mensajes muertos los que superen el máximo de intentos.

#### Scenario: Reintento exitoso
- **WHEN** el primer envío falla y el segundo responde correctamente
- **THEN** la notificación pasa a `ENVIADA` sin duplicar el aviso

#### Scenario: Máximo de intentos
- **WHEN** una notificación supera la cantidad máxima de intentos
- **THEN** queda `FALLIDA`, se registra el error y el mensaje se deriva a la DLQ

#### Scenario: Evento duplicado
- **WHEN** se recibe dos veces el mismo evento de dominio
- **THEN** se envía una sola notificación por destinatario y tipo

### Requirement: Independencia de las operaciones principales

El sistema SHALL garantizar que una falla o demora de notification-service no bloquee ni revierta una compra, una devolución o una cancelación.

#### Scenario: Servicio de notificaciones caído
- **WHEN** notification-service no está disponible al confirmarse una compra
- **THEN** la compra se completa y el evento queda encolado para cuando el servicio se recupere

#### Scenario: Cola acumulada
- **WHEN** notification-service se reinicia con mensajes pendientes
- **THEN** retoma el consumo sin perder los avisos ni duplicar los ya enviados

### Requirement: Protección de datos personales

El sistema SHALL registrar y enviar solo los datos necesarios del destinatario y SHALL evitar tokens, contraseñas o datos completos de pago en logs y notificaciones.

#### Scenario: Log de envío
- **WHEN** se registra el envío de una notificación
- **THEN** el email se muestra enmascarado y no se incluyen tokens ni datos de pago

#### Scenario: Destinatario sin email
- **WHEN** una orden no tiene email de contacto
- **THEN** la notificación queda `FALLIDA` con un motivo claro y no se inventa un destinatario
