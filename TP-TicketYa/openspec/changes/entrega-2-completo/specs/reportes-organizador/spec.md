# Spec Delta

## Purpose

Permite a los organizadores consultar el desempeño de sus eventos con entradas vendidas y devueltas por sector, ocupación y recaudación, respetando la propiedad del evento.

## ADDED Requirements

### Requirement: Reporte por evento

El sistema SHALL exponer un reporte por evento con entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación por sector y total.

#### Scenario: Reporte de un evento propio
- **WHEN** un organizador consulta el reporte de un evento que le pertenece
- **THEN** recibe por cada sector las entradas vendidas, devueltas, ocupación y recaudación, y los totales del evento

#### Scenario: Evento sin ventas
- **WHEN** el evento no tiene entradas vendidas
- **THEN** el reporte devuelve ceros y la ocupación en 0 %, sin error

#### Scenario: Evento inexistente
- **WHEN** se consulta el reporte de un evento que no existe
- **THEN** se responde 404

### Requirement: Autorización del reporte

El sistema SHALL permitir el reporte solo al organizador dueño del evento o a un administrador.

#### Scenario: Otro organizador
- **WHEN** un organizador consulta el reporte de un evento ajeno
- **THEN** se responde 403 y no se exponen datos de ventas

#### Scenario: Administrador
- **WHEN** un administrador consulta el reporte de cualquier evento
- **THEN** se responde 200 con el reporte completo

#### Scenario: Cliente
- **WHEN** un cliente intenta consultar un reporte
- **THEN** se responde 403

### Requirement: Definiciones del reporte

El sistema SHALL calcular las métricas de forma consistente: vendidas como entradas pagadas menos devueltas, ocupación como vendidas sobre capacidad del sector y recaudación como cobros menos reintegros.

#### Scenario: Cálculo por sector
- **WHEN** un sector tiene 100 lugares, 40 entradas pagadas y 5 devueltas
- **THEN** el reporte informa 40 vendidas, 5 devueltas, 35 netas y 35 % de ocupación

#### Scenario: Recaudación con devoluciones
- **WHEN** se devuelve una entrada y se reintegra su importe
- **THEN** la recaudación del sector y del evento descuenta el reintegro

#### Scenario: Zona no numerada
- **WHEN** el sector es una zona no numerada con capacidad máxima
- **THEN** la ocupación se calcula sobre esa capacidad y las entradas vendidas de la zona

### Requirement: Datos consistentes entre servicios

El sistema SHALL construir el reporte con los datos de ventas de orders-service y la capacidad y precios de catalog-service, tolerando una consistencia eventual acotada.

#### Scenario: Catálogo disponible
- **WHEN** se consulta el reporte y catalog-service responde
- **THEN** el reporte usa la capacidad y los precios vigentes del evento

#### Scenario: Catálogo degradado
- **WHEN** catalog-service no responde y existe una copia en cache vigente
- **THEN** el reporte se devuelve con la copia y se indica que los datos pueden estar desactualizados

#### Scenario: Sin datos de capacidad
- **WHEN** no hay forma de obtener la capacidad de un sector
- **THEN** el reporte informa las ventas y la recaudación, y marca la ocupación como no disponible

### Requirement: Reporte actualizado tras devoluciones y cancelaciones

El sistema SHALL reflejar en el reporte las devoluciones aprobadas y las cancelaciones de evento.

#### Scenario: Devolución aprobada
- **WHEN** se aprueba una devolución
- **THEN** la próxima consulta del reporte descuenta la entrada y el reintegro

#### Scenario: Evento cancelado
- **WHEN** se cancela un evento con entradas vendidas
- **THEN** el reporte muestra las entradas como reintegradas y la recaudación neta correspondiente
