# Design

## Context

Ver `proposal.md` para la motivación. La UI es un extra opcional de la Entrega 2: consume el gateway existente y los endpoints que agrega `entrega-2-completo`, sin cambiar contratos ni datos. El backend sigue siendo la fuente de verdad.

Restricciones:

- El cliente entra siempre por `http://localhost:8080`; no se llaman los puertos internos.
- El JWT lo emite el gateway; la UI no valida la firma, solo maneja el ciclo de sesión.
- El proyecto debe poder correr sin backend para revisar el esqueleto visual, pero las pantallas reales dependen de la API.
- La UI no se entrega si el backend no está estable.

## Goals / Non-Goals

**Goals:**

- Dejar un esqueleto navegable y compilable con las pantallas del flujo del cliente.
- Centralizar el acceso a la API y el manejo de errores/sesión.
- Evitar CORS en desarrollo con un proxy de Vite y resolverlo en producción con Nginx.
- Poder dockerizar la UI sin tocar el compose integral hasta que se apruebe.

**Non-Goals:**

- Backoffice administrativo u organizer completo.
- Pagos reales, mapas 3D, WebSockets, SSR o SEO.
- Tests E2E de navegador y accesibilidad WCAG completa.
- Reemplazar Postman como herramienta de verificación del backend.

## Decisions

### 1. Stack: React + Vite + TypeScript

- React 19 + Vite 7 + TypeScript, sin framework de UI ni librería de estado.
- `react-router-dom` para rutas: `/login`, `/`, `/events/:id`, `/checkout`, `/tickets`.
- CSS propio con variables y layout responsive; se evita Tailwind para no sumar configuración ni dependencias.
- `fetch` nativo envuelto en `src/api/client.ts`; no se agrega Axios.

**Alternativas consideradas:** Angular/Vue (válidos, pero el equipo ya conoce React y Vite es el camino más corto); Next.js (SSR innecesario); Thymeleaf (mezcla la UI con el backend y no aprovecha el gateway).

### 2. Autenticación y sesión

- El login llama `POST /api/auth/login` y guarda el JWT en `sessionStorage` (no `localStorage` persistente).
- `AuthContext` expone `token`, `user`, `login`, `logout` y `isAuthenticated`.
- El cliente HTTP agrega `Authorization: Bearer <token>` y, ante 401, dispara `logout` y redirige a `/login`.
- No se guardan contraseñas ni datos sensibles. El email del usuario se muestra desde el claim o la respuesta del login.
- Para el esqueleto, si el endpoint de auth todavía no existe, la pantalla muestra un error controlado y un botón de reintento; no se simula un usuario real.

**Alternativas consideradas:** cookies HttpOnly (más seguras, pero el gateway aún no las emite); guardar el token en memoria (se pierde al refrescar y complica la demo); OAuth2 (fuera de alcance).

### 3. API client y contratos

- `src/api/client.ts`: base `/api`, `get/post/put`, parseo de `application/problem+json`, timeout con `AbortController` y errores tipados (`ApiError`).
- `src/api/events.ts`, `src/api/orders.ts`, `src/api/auth.ts`: funciones por dominio, no llamadas sueltas en componentes.
- Los DTOs se tipan a mano en `src/api/types.ts`; se mantienen alineados a `docs/api-contract.md` y OpenAPI.
- La `Idempotency-Key` del checkout se genera con `crypto.randomUUID()` al iniciar la compra y se reutiliza en reintentos.

### 4. Pantallas y estado

| Ruta | Pantalla | Estado |
|---|---|---|
| `/login` | Login | Formulario, error, loading |
| `/` | Búsqueda | Filtros, página, resultados, vacío/error |
| `/events/:id` | Detalle | Evento, sectores, asientos/cantidad, selección |
| `/checkout` | Checkout | Reserva, medio de pago mock, idempotencia, resultado |
| `/tickets` | Mis entradas | Listado y detalle con QR |
| `*` | 404 | Enlace a búsqueda |

El estado de selección vive en `CheckoutContext`/`useState` de la pantalla y se pierde al refrescar; la reserva real en inventory es la que manda. No se agrega Redux/Zustand.

### 5. Proxy, CORS y despliegue

- Desarrollo: `vite.config.ts` con `server.proxy['/api'] = 'http://localhost:8080'`; la UI llama rutas relativas.
- Producción: `nginx.conf` sirve `dist/` y hace `proxy_pass http://api-gateway:8080;` para `/api/`.
- `Dockerfile` multi-stage (`node:22-alpine` build + `nginx:alpine`) y `.dockerignore`.
- El servicio `web` del compose se agrega solo cuando la UI se apruebe; por ahora queda el Dockerfile y las instrucciones.
- Si se sirve en otro host, `VITE_API_URL` permite cambiar la base; por defecto es `/api`.

### 6. Testing y verificación

- `npm run build` debe pasar sin errores de TypeScript.
- `npm run lint` con ESLint (configuración mínima) y `npm run test` con Vitest para utilidades del cliente HTTP.
- Para el esqueleto alcanza con build + lint; los tests de componentes se agregan si la UI se entrega.
- La verificación funcional se hace con el backend levantado: login, búsqueda, detalle, reserva, pago mock y “Mis entradas”.

## Risks / Trade-offs

- **El backend de auth/JWT todavía no está implementado** → la UI muestra errores controlados; el esqueleto se prueba con build y rutas, no con login real hasta la tarea 2.1.
- **Contratos en evolución** → los tipos se concentran en `src/api/types.ts` y se actualizan junto al contrato; no se dispersan en componentes.
- **La UI puede consumir tiempo del backend** → se trabaja en un PR separado y solo después de los P0; si no llega, se cierra sin afectar la entrega.
- **CORS en producción** → Nginx hace de proxy same-origin; no se habilita CORS amplio en el gateway.
- **QR** → se muestra el código y un placeholder visual; la generación de imagen QR real se puede agregar con una librería si se entrega.
- **Accesibilidad** → se cubren etiquetas, foco y teclado en los controles principales; una auditoría completa queda fuera de alcance.

## Migration Plan

1. Crear `web/` con Vite, TypeScript y las rutas base.
2. Implementar `AuthContext`, cliente HTTP y layout.
3. Implementar pantallas de búsqueda y detalle con datos reales.
4. Implementar checkout y “Mis entradas”.
5. Agregar Dockerfile/Nginx y documentación.
6. Si se aprueba la entrega, agregar el servicio `web` al compose integral y el enlace en el README raíz.

Rollback: al ser un proyecto separado, se elimina `web/` y el servicio del compose; no afecta a los microservicios.

## Open Questions

Ninguna que cambie el esqueleto. Quedan para la implementación completa: librería de QR, mapa de asientos con coordenadas reales y estrategia de refresh token (hoy no existe refresh).
