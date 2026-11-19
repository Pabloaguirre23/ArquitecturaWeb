# Spec Delta

## Purpose

Permite autenticar a los usuarios de TicketYa y autorizar cada operación según su rol en el punto de entrada único, sin confiar en encabezados enviados por el cliente.

## ADDED Requirements

### Requirement: Inicio de sesión con credenciales

El sistema SHALL permitir a un usuario autenticarse con email y contraseña y SHALL devolver un JWT con su identidad y rol cuando las credenciales sean válidas.

#### Scenario: Credenciales válidas
- **WHEN** un usuario registrado envía email y contraseña correctos a `POST /api/auth/login`
- **THEN** recibe 200 con un JWT, su rol, su email y la fecha de expiración

#### Scenario: Credenciales inválidas
- **WHEN** un usuario envía una contraseña incorrecta o un email inexistente
- **THEN** recibe 401 y no se emite ningún token

#### Scenario: Datos incompletos
- **WHEN** la solicitud de login no incluye email o contraseña
- **THEN** recibe 400 con el detalle de los campos faltantes

### Requirement: Token con identidad y rol

El token emitido SHALL incluir el identificador del usuario, su rol, su email y una expiración, y SHALL estar firmado para impedir modificaciones.

#### Scenario: Contenido del token
- **WHEN** se decodifica un token emitido
- **THEN** contiene `sub`, `role`, `email` y `exp`, y no contiene la contraseña ni datos sensibles

#### Scenario: Token alterado o vencido
- **WHEN** un cliente presenta un token con firma inválida o expirado
- **THEN** el gateway responde 401 y no reenvía la solicitud al servicio

### Requirement: Validación centralizada en el gateway

El gateway SHALL validar el JWT de toda solicitud a un endpoint protegido y SHALL derivar de él los encabezados `X-User-Id`, `X-User-Role` y `X-User-Email` que consumen los servicios.

#### Scenario: Solicitud protegida sin token
- **WHEN** se llama a un endpoint protegido sin encabezado `Authorization: Bearer`
- **THEN** el gateway responde 401 en formato RFC 7807

#### Scenario: Encabezados de identidad falsificados
- **WHEN** un cliente envía `X-User-Id`, `X-User-Role` o `X-User-Email` por su cuenta
- **THEN** el gateway los descarta y usa exclusivamente los datos del JWT

#### Scenario: Endpoint público
- **WHEN** se consulta un endpoint público de catálogo sin token
- **THEN** la solicitud se atiende sin exigir autenticación

### Requirement: Autorización por rol

El sistema SHALL restringir cada endpoint a los roles definidos en la matriz de acceso y SHALL responder 403 cuando el rol autenticado no esté permitido.

#### Scenario: Rol no autorizado
- **WHEN** un cliente con rol `CLIENT` intenta publicar un evento
- **THEN** el gateway responde 403 y no invoca catalog-service

#### Scenario: Personal de acceso
- **WHEN** un usuario con rol `ACCESS_STAFF` intenta validar un QR
- **THEN** la operación se permite, pero el mismo usuario no puede crear eventos ni consultar reportes

#### Scenario: Ruta sin regla explícita
- **WHEN** se invoca una ruta que no figura en la matriz de acceso
- **THEN** el sistema la deniega por defecto

### Requirement: Protección de endpoints internos

Los endpoints `/internal/**` SHALL ser accesibles solo entre servicios y SHALL requerir un token interno que el gateway no acepta ni propaga desde el exterior.

#### Scenario: Llamada externa a un endpoint interno
- **WHEN** un cliente intenta llamar a `/internal/**` a través del gateway
- **THEN** recibe 404 o 403 y la solicitud no llega al servicio

#### Scenario: Llamada interna sin token
- **WHEN** un servicio llama a `/internal/**` sin `X-Internal-Token` o con un valor inválido
- **THEN** el servicio destino responde 401 y no ejecuta la operación

### Requirement: Usuarios y contraseñas seguras

El sistema SHALL almacenar las contraseñas con un hash seguro y SHALL sembrar usuarios de prueba para cada rol sin credenciales en texto plano en el repositorio.

#### Scenario: Contraseña almacenada
- **WHEN** se inspecciona la base de usuarios
- **THEN** la contraseña figura como hash BCrypt y nunca como texto plano

#### Scenario: Usuarios de prueba
- **WHEN** se levanta el entorno de desarrollo
- **THEN** existen usuarios de prueba para `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF` documentados en el README
