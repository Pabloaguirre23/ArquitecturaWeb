# Tasks

## 1. Esqueleto del proyecto

- [x] 1.1 Crear `web/` con Vite, React, TypeScript y `react-router-dom`; verificar `npm install` y `npm run build` con la pantalla inicial.
- [x] 1.2 Configurar `vite.config.ts` con proxy `/api` al gateway, variables `VITE_API_URL` y `index.html` con metadatos de TicketYa.
- [x] 1.3 Crear layout, navegación, estilos base y pantalla 404; verificar navegación entre rutas y responsive a 375 px.

## 2. Sesión y cliente HTTP

- [x] 2.1 Implementar `AuthContext` con login, logout, token en `sessionStorage` y redirección ante 401; verificar con un mock del endpoint de login.
- [x] 2.2 Implementar `src/api/client.ts` con `fetch`, timeout, parseo RFC 7807 y `ApiError`; verificar con Vitest los casos 200, 4xx y error de red.
- [x] 2.3 Tipar los DTOs de auth, eventos, reservas, órdenes y entradas en `src/api/types.ts`; verificar que el build no tenga `any` en las respuestas.

## 3. Pantallas del cliente

- [x] 3.1 Implementar login y guardas de ruta; verificar credenciales válidas, inválidas y sesión vencida en modo demo.
- [x] 3.2 Implementar búsqueda con filtros, paginación, estados de carga, vacío y error; verificar en modo demo (el gateway real se prueba en 5.2).
- [x] 3.3 Implementar detalle de evento con sectores, precios y disponibilidad; verificar evento numerado y zona no numerada en modo demo.
- [x] 3.4 Implementar selección de asientos/cantidad y resumen de compra; verificar límites y total estimado en modo demo.
- [x] 3.5 Implementar checkout con reserva, `Idempotency-Key`, pago mock y resultado; verificar aprobado, rechazado y vencido en modo demo.
- [x] 3.6 Implementar “Mis entradas” con listado y detalle/QR; verificar en modo demo que solo se vean las propias.

## 4. Integración y despliegue

- [x] 4.1 Actualizar `web/README.md` con requisitos, comandos, variables y flujo de demo.
- [x] 4.2 Crear `Dockerfile` multi-stage y `nginx.conf` con proxy `/api`; verificar build de imagen, health y servido estático.
- [ ] 4.3 Si la UI se aprueba, agregar el servicio `web` al compose integral y el enlace en el README raíz; verificar `docker compose up --build`.

## 5. Verificación

- [x] 5.1 Ejecutar `npm run build`, `npm run lint` y `npm run test`; verificar todo verde.
- [ ] 5.2 Probar el flujo completo con el backend levantado: login → búsqueda → detalle → reserva → pago → entradas; guardar evidencia.
- [x] 5.3 Revisar responsive y teclado en las pantallas principales; verificar que no haya desbordes horizontales. _Hecho: skip link, foco visible, `aria-*` en asientos/errores y auditoría a 375/1280 px; evidencia en `docs/evidencia/entrega-2-ui-accesibilidad.txt`._

## Workflow follow-up

- Si la UI no se entrega, cerrar el PR y dejar el change sin archivar como trabajo futuro.
- Si se entrega, archivar el change con `openspec archive interfaz-cliente-web`.
