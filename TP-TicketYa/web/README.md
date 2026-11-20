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
| `/para-vos` | Preferencias y recomendaciones explicables |
| `/access` | Control de acceso: validar QR (ACCESS_STAFF/ADMIN) |
| `/reports` | Reporte por evento y sector (ORGANIZER/ADMIN) |

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

### Recomendaciones y preferencias

La pantalla **Para vos** (`/para-vos`) permite guardar tipos de evento, artistas favoritos y
ciudad, y muestra recomendaciones con el motivo de cada sugerencia. En modo demo:

- Arranca con preferencias de ejemplo (`RECITAL`, `Los Piojos`, `Tandil`) para que la pantalla
  tenga contenido desde el primer ingreso.
- Persiste las preferencias en `sessionStorage` y recalcula las sugerencias al guardar.
- Excluye los eventos que el cliente ya compró (las entradas demo incluyen una de teatro).
- El detalle de cada evento muestra “También te puede gustar” con eventos similares por tipo,
  artistas y ciudad.

En modo gateway real usa `GET /api/recommendations`, `GET/PUT /api/recommendations/preferences`
y `GET /api/recommendations/events/{eventId}/similar`. Igual que el login, estas rutas dependen
de que P4 agregue `/api/recommendations/**` al gateway y de la tarea 2.1; hasta entonces la
forma de recorrerlas es el modo demo.

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

> El login real usa `POST /api/auth/login` del gateway (JWT de 2.1/2.2, ya mergeado). El
> modo demo sigue disponible para revisar la UI sin backend.

## Comandos

```bash
cd web
npm install
npm run dev       # http://localhost:5173
npm run build     # TypeScript + build de producción en dist/
npm run lint      # oxlint
npm run test      # Vitest (cliente HTTP, layout, asientos, tarjetas y mock de recomendaciones)
```

## Docker

```bash
cd web
docker build -t ticketya/web:local .
docker run --rm -p 3000:80 ticketya/web:local
```

El `nginx.conf` espera un host `api-gateway` en la red del compose. El servicio `web` ya está
incluido en el `docker-compose.yml` integral y se publica en `http://localhost:3000`.

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
La prueba contra el backend real (5.2) quedó desbloqueada con el JWT: login, búsqueda,
recomendaciones y entradas responden por el proxy de Vite/Nginx.

## Validaciones y QA

- El buscador valida que “Desde” no sea posterior a “Hasta” antes de llamar a la API; el error
  queda asociado a ambos campos con `aria-invalid`/`aria-describedby`.
- La cantidad de lugares se puede borrar y editar libremente; al salir del campo se normaliza
  al rango `[1, máximo]`.
- La pantalla “Para vos” solo se muestra a usuarios `CLIENT`; los errores de preferencias se
  limpian al editar.
- La QA de interacción y responsive con Chrome headless está en
  [`docs/evidencia/entrega-2-ui-qa.txt`](../docs/evidencia/entrega-2-ui-qa.txt).

## Estructura

```text
web/
  src/
    api/         cliente HTTP, tipos, adaptadores, datos demo y recomendaciones
    auth/        contexto de sesión, storage y ruta protegida
    components/  layout, tarjetas, estados, mapa de asientos y QR
    pages/       login, búsqueda, detalle, checkout, entradas, para vos y 404
    utils/       formato, errores, tipos de evento, idempotencia y storage
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
