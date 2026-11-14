# TicketYa — Interfaz web del cliente

Interfaz opcional de la Entrega 2 para demostrar el flujo principal del cliente contra el API
Gateway. **No es parte del alcance comprometido**: se entrega solo si el backend está estable.
El change de OpenSpec es
[`../openspec/changes/interfaz-cliente-web/`](../openspec/changes/interfaz-cliente-web/).

## Pantallas

| Ruta | Pantalla |
|---|---|
| `/login` | Ingreso con email y contraseña |
| `/` | Búsqueda de eventos con filtros y paginación |
| `/events/:id` | Detalle, sectores y selección de asientos o cantidad |
| `/checkout` | Confirmación de la compra y pago mock |
| `/tickets` | Mis entradas con QR |

## Requisitos

- Node.js 20+ (probado con Node 26)
- npm 10+
- Opcional: el backend levantado por el gateway (`http://localhost:8080`)

## Modo demo

Por defecto la interfaz arranca con `VITE_USE_MOCKS=true`: usa eventos, asientos, reservas y
tickets de ejemplo en memoria. Sirve para revisar el diseño y navegar sin backend.

Credenciales demo:

```text
Email: cliente@ticketya.test
Password: demo
```

El flujo de compra simula la pasarela con las opciones **Aprobado**, **Rechazado** y **Lento**.
Las entradas compradas se guardan en `sessionStorage` y aparecen en “Mis entradas”.

## Modo gateway real

Crear `web/.env` a partir de `.env.example`:

```bash
cd web
cp .env.example .env
```

Cambiar:

```text
VITE_USE_MOCKS=false
VITE_API_URL=/api
VITE_GATEWAY_URL=http://localhost:8080
```

Con el gateway levantado en `8080`, Vite hace proxy de `/api` y no hay problemas de CORS en
desarrollo. Para producción, Nginx hace el mismo proxy hacia `api-gateway:8080` dentro del
compose.

> El login real depende de la tarea 2.1 (JWT en el gateway). Hasta que esté implementada, el
> modo demo es la forma de recorrer la UI.

## Comandos

```bash
cd web
npm install
npm run dev       # http://localhost:5173
npm run build     # TypeScript + build de producción en dist/
npm run lint      # oxlint
npm run test      # Vitest (cliente HTTP, layout y mapa de asientos)
```

## Docker

```bash
cd web
docker build -t ticketya/web:local .
docker run --rm -p 3000:80 ticketya/web:local
```

El `nginx.conf` espera un host `api-gateway` en la red del compose. Cuando la UI se apruebe, se
agrega el servicio `web` al `docker-compose.yml` integral y el enlace en el README raíz.

## Accesibilidad y responsive

- **Skip link:** primer elemento de la página; con Tab se enfoca “Saltar al contenido” y Enter
  lleva el foco a `<main id="main-content">`.
- **Foco visible:** `:focus-visible` con contorno de 3 px en enlaces, botones, inputs y selects;
  el mapa de asientos conserva `aria-pressed` y etiquetas por fila/asiento.
- **Formularios:** los errores de login y checkout usan `role="alert"` y se asocian con
  `aria-describedby`/`aria-invalid`; los campos tienen `label` explícito.
- **Movimiento reducido:** `prefers-reduced-motion` desactiva transiciones y scroll suave.
- **Sin desborde horizontal:** verificado a 375 px y 1280 px. El mapa de asientos es la única
  zona con scroll horizontal interno (`overflow-x: auto`), no la página.

La verificación de teclado y responsive se registra en
[`docs/evidencia/entrega-2-ui-accesibilidad.txt`](../docs/evidencia/entrega-2-ui-accesibilidad.txt).
La prueba contra el backend real (5.2) sigue bloqueada hasta que el gateway tenga JWT (tarea 2.1).

## Estructura

```text
web/
  src/
    api/         cliente HTTP, tipos, adaptadores y datos demo
    auth/        contexto de sesión, storage y ruta protegida
    components/  layout, tarjetas, estados, mapa de asientos y QR
    pages/       login, búsqueda, detalle, checkout, entradas y 404
    utils/       formato, errores, idempotencia y storage
  Dockerfile
  nginx.conf
```

## Variables

| Variable | Default | Uso |
|---|---|---|
| `VITE_API_URL` | `/api` | Base de la API para el cliente HTTP |
| `VITE_GATEWAY_URL` | `http://localhost:8080` | Destino del proxy de Vite en desarrollo |
| `VITE_USE_MOCKS` | `true` | `false` para consumir el gateway real |
| `VITE_DEMO_EMAIL` | `cliente@ticketya.test` | Email precargado en modo demo |
| `VITE_DEMO_PASSWORD` | `demo` | Contraseña precargada en modo demo |
