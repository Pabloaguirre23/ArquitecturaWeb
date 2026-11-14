# Spec Delta

## Purpose

Define el empaquetado de cada microservicio en su propio contenedor y el arranque integral del sistema con un único `docker-compose.yml`, incluyendo bases, Redis, MongoDB, RabbitMQ y observabilidad.

## ADDED Requirements

### Requirement: Contenedor independiente por servicio

Cada microservicio SHALL tener su propio Dockerfile y SHALL poder construirse y ejecutarse de forma independiente del resto.

#### Scenario: Build individual
- **WHEN** se construye la imagen de un servicio
- **THEN** la imagen se genera solo con su código y sus dependencias, sin requerir los otros servicios

#### Scenario: Ejecución individual
- **WHEN** se ejecuta el contenedor de un servicio con las variables de entorno necesarias
- **THEN** el servicio arranca y expone su endpoint de salud

#### Scenario: Usuario no root
- **WHEN** se inspecciona el contenedor
- **THEN** el proceso corre con un usuario sin privilegios

### Requirement: Compose integral

El sistema SHALL poder levantarse completo con un único `docker-compose.yml` que incluya gateway, microservicios, bases de datos, Redis, MongoDB, RabbitMQ y el stack de observabilidad.

#### Scenario: Arranque completo
- **WHEN** se ejecuta `docker compose up --build`
- **THEN** todos los contenedores arrancan en el orden correcto y el gateway responde saludable

#### Scenario: Dependencias listas
- **WHEN** un servicio arranca antes que su base o broker
- **THEN** el compose espera el healthcheck correspondiente antes de iniciarlo

#### Scenario: Persistencia
- **WHEN** se reinicia el compose sin borrar volúmenes
- **THEN** los datos de las bases, MongoDB y Redis se conservan

### Requirement: Configuración por entorno

El sistema SHALL externalizar puertos, URLs, credenciales y secretos en variables de entorno y SHALL incluir un `.env.example` sin secretos reales.

#### Scenario: Cambio de puerto
- **WHEN** se cambia el puerto de un servicio por variable de entorno
- **THEN** el compose y el gateway usan el nuevo valor

#### Scenario: Secretos
- **WHEN** se revisa el repositorio
- **THEN** no hay contraseñas, claves JWT ni tokens reales commiteados

#### Scenario: Valores por defecto de desarrollo
- **WHEN** se levanta el compose sin `.env`
- **THEN** usa valores de desarrollo documentados y el sistema funciona

### Requirement: Aislamiento de red

El sistema SHALL exponer al exterior solo el gateway y SHALL mantener las bases, Redis, MongoDB y RabbitMQ en una red interna sin puertos públicos obligatorios.

#### Scenario: Acceso externo
- **WHEN** un cliente intenta llegar a un microservicio por su puerto interno
- **THEN** no puede hacerlo desde fuera de la red del compose

#### Scenario: Acceso interno
- **WHEN** un servicio necesita otra dependencia
- **THEN** la resuelve por el nombre del servicio dentro de la red de Docker

### Requirement: Verificación del despliegue

El sistema SHALL incluir una verificación automatizada de que el compose es válido y de que el entorno levanta y responde.

#### Scenario: Validación de configuración
- **WHEN** se ejecuta `docker compose config`
- **THEN** la configuración se valida sin errores

#### Scenario: Smoke test
- **WHEN** el compose termina de levantar
- **THEN** un script verifica salud del gateway y de cada servicio y ejecuta un flujo mínimo de compra

#### Scenario: Clone limpio
- **WHEN** otra persona clona el repositorio y sigue el README
- **THEN** puede levantar todo el sistema y ejecutar el smoke test sin pasos ocultos
