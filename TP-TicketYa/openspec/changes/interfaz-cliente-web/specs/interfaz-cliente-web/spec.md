# Spec Delta

## Purpose

Ofrece una interfaz web mínima para que un cliente de TicketYa se autentique, descubra eventos, seleccione lugares, compre entradas y consulte sus compras a través del API Gateway.

## ADDED Requirements

### Requirement: Inicio de sesión

La interfaz SHALL permitir a un cliente autenticarse con email y contraseña contra el gateway y SHALL conservar el JWT para las llamadas siguientes.

#### Scenario: Credenciales válidas
- **WHEN** el cliente ingresa email y contraseña correctos
- **THEN** la aplicación guarda el token, muestra su nombre/email y navega a la búsqueda de eventos

#### Scenario: Credenciales inválidas
- **WHEN** el gateway responde 401
- **THEN** la aplicación muestra el mensaje de error y no guarda ningún token

#### Scenario: Sesión vencida
- **WHEN** una llamada protegida responde 401 por token vencido
- **THEN** la aplicación limpia la sesión y redirige al login

#### Scenario: Cierre de sesión
- **WHEN** el cliente elige cerrar sesión
- **THEN** se elimina el token y se vuelve al login

### Requirement: Búsqueda de eventos

La interfaz SHALL permitir buscar eventos publicados por texto, tipo, ciudad y rango de fechas, y SHALL mostrar el total y la paginación.

#### Scenario: Búsqueda con resultados
- **WHEN** el cliente ingresa un criterio y presiona buscar
- **THEN** se muestran las tarjetas de eventos con nombre, fecha, lugar, tipo y precio mínimo

#### Scenario: Sin resultados
- **WHEN** la búsqueda no devuelve eventos
- **THEN** se muestra un estado vacío con la opción de limpiar los filtros

#### Scenario: Error del gateway
- **WHEN** la búsqueda responde 4xx o 5xx
- **THEN** se muestra un mensaje de error legible y se permite reintentar

### Requirement: Detalle de evento y selección de lugares

La interfaz SHALL mostrar el detalle de un evento con sus sectores, precios y disponibilidad, y SHALL permitir elegir asientos numerados o cantidad de lugares en una zona no numerada.

#### Scenario: Evento numerado
- **WHEN** el cliente abre un evento con sectores numerados
- **THEN** ve el mapa de asientos, puede seleccionar hasta el límite permitido y se muestra el total estimado

#### Scenario: Zona no numerada
- **WHEN** el cliente abre un sector no numerado
- **THEN** puede indicar una cantidad dentro de la disponibilidad y se muestra el total estimado

#### Scenario: Sin disponibilidad
- **WHEN** un sector o asiento no está disponible
- **THEN** se muestra como no seleccionable y no se permite agregarlo al carrito

#### Scenario: Evento inexistente
- **WHEN** el evento no existe o no está publicado
- **THEN** se muestra una pantalla 404 con un enlace para volver a la búsqueda

### Requirement: Checkout y compra

La interfaz SHALL permitir reservar los lugares elegidos, iniciar la compra con una clave de idempotencia y mostrar el resultado del pago.

#### Scenario: Compra aprobada
- **WHEN** el cliente confirma la compra y el pago es aprobado
- **THEN** se muestra la confirmación con el total y un enlace a “Mis entradas”

#### Scenario: Pago rechazado
- **WHEN** el pago es rechazado
- **THEN** se muestra el motivo, no se muestran entradas y se permite volver al evento

#### Scenario: Reserva vencida
- **WHEN** la reserva vence antes del pago
- **THEN** se informa que los lugares se liberaron y se ofrece volver a seleccionar

#### Scenario: Reintento de pago
- **WHEN** el cliente reintenta la compra por un error de conexión
- **THEN** se reutiliza la misma `Idempotency-Key` y no se duplica la orden

### Requirement: Mis entradas

La interfaz SHALL permitir al cliente ver sus compras y entradas con evento, sector, asiento y código QR.

#### Scenario: Listado de entradas
- **WHEN** el cliente abre “Mis entradas”
- **THEN** ve solo sus entradas, ordenadas por fecha del evento

#### Scenario: Detalle de entrada
- **WHEN** el cliente selecciona una entrada
- **THEN** ve el QR y los datos del evento, sector y asiento

#### Scenario: Sin entradas
- **WHEN** el cliente no tiene compras
- **THEN** se muestra un estado vacío con un enlace a la búsqueda

### Requirement: Manejo de errores y estados de carga

La interfaz SHALL mostrar estados de carga, errores legibles y acciones de reintento en todas las pantallas que consumen el gateway.

#### Scenario: Carga inicial
- **WHEN** una pantalla espera datos del gateway
- **THEN** muestra un indicador de carga y deshabilita acciones incompatibles

#### Scenario: Error de red
- **WHEN** el gateway no responde
- **THEN** se muestra un mensaje de conexión y un botón para reintentar

#### Scenario: Error de negocio
- **WHEN** el gateway responde con RFC 7807
- **THEN** se muestra el campo `detail` y, si existe, el código de negocio

### Requirement: Diseño responsive y accesible

La interfaz SHALL ser usable en escritorio y en un ancho de móvil, con navegación por teclado y etiquetas accesibles en los controles principales.

#### Scenario: Vista móvil
- **WHEN** la pantalla tiene un ancho menor a 768 px
- **THEN** la navegación y las tarjetas se apilan sin desbordar horizontalmente

#### Scenario: Navegación por teclado
- **WHEN** el cliente recorre la aplicación con Tab
- **THEN** puede llegar a los enlaces, filtros y botones principales y ve el foco

#### Scenario: Formularios
- **WHEN** un campo tiene un error
- **THEN** el mensaje está asociado al campo y es legible por un lector de pantalla

### Requirement: Ejecución y despliegue

La interfaz SHALL poder ejecutarse en desarrollo con Vite y en producción como archivos estáticos servidos por Nginx, apuntando al gateway.

#### Scenario: Desarrollo
- **WHEN** se ejecuta `npm run dev`
- **THEN** Vite sirve la aplicación en el puerto 5173 y redirige `/api` al gateway local

#### Scenario: Build de producción
- **WHEN** se ejecuta `npm run build`
- **THEN** se generan archivos estáticos en `dist/` sin errores de TypeScript

#### Scenario: Contenedor
- **WHEN** se construye la imagen de `web/`
- **THEN** Nginx sirve la aplicación y redirige `/api` al gateway del compose
