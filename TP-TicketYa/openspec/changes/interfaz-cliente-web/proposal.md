## Why

El enunciado valora positivamente que TicketYa no sea solo backend. Una interfaz web mínima permite demostrar el flujo principal del cliente (buscar, elegir sector y asientos, comprar y ver las entradas) sin depender de Postman y hace más creíble la defensa del TP.

Es un extra opcional: no reemplaza ningún requisito de la Entrega 2 y solo se entrega si el backend está estable.

## What Changes

- Nuevo proyecto `web/` con React + Vite + TypeScript.
- Login contra `POST /api/auth/login` y manejo del JWT en memoria/`sessionStorage`.
- Pantallas de búsqueda, detalle de evento con sectores y asientos, checkout, mis entradas y reporte básico para organizador.
- Cliente HTTP centralizado contra el gateway, con manejo de errores RFC 7807 y renovación/expiración de sesión.
- Proxy de desarrollo de Vite hacia `http://localhost:8080` y configuración de Nginx para el contenedor.
- Dockerfile y documentación de ejecución; sin cambios de contrato en el backend.

## Capabilities

### New Capabilities

- `interfaz-cliente-web`: experiencia web del cliente para autenticarse, descubrir eventos, comprar entradas y consultar sus compras.

### Modified Capabilities

Ninguna. La UI consume los contratos existentes y los que agrega `entrega-2-completo`; no cambia el comportamiento del backend.

## Impact

**Código nuevo:** `web/` (React, Vite, TypeScript, React Router, CSS propio).

**Infraestructura:** `web/Dockerfile`, `web/nginx.conf` y, si se aprueba, un servicio `web` en el compose integral.

**Dependencias:** Node 20+ para desarrollo; el backend sigue siendo la única fuente de datos.

**Fuera de alcance:** backoffice administrativo, app móvil, pagos reales, edición de eventos por parte del organizador, accesibilidad WCAG completa y tests E2E de navegador. La UI es una demostración de los flujos del cliente.
