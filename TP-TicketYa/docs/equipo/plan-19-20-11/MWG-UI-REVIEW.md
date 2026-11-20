# Modern Web Guidance — revisión de la UI

**Fecha:** 20/11/2026
**Alcance:** `web/` (React 19 + TypeScript + Vite), modo demo y modo real.

## Qué se corrió

```bash
cd web
DISABLE_TELEMETRY=1 npx -y modern-web-guidance@latest legacy-scan .
```

Resultado: **0 hallazgos** en 38 guías calificadas (el scan cubre ~20 % del corpus y saltea
8 archivos de docs/tests). No hay patrones legacy de los que detecta el analyzer.

Después se hicieron búsquedas/retrieval dirigidos:

- `autofill-sign-in-form`, `validate-input-after-interaction`, `accessible-error-announcement`
- `responsive-table`
- `navbar`, `forms`
- `faster-spa-view-transitions`, `same-document-transitions`
- `card`, `size-aware-styling`
- `security`

## Lo que ya está bien (no tocar)

- `<label>` explícito en todos los campos, `required`, `type="email"` y
  `autocomplete="current-password"` en login.
- `role="alert"` en errores, `aria-invalid`/`aria-describedby` en validaciones locales de
  búsqueda y cantidad.
- Skip link, foco visible, `prefers-reduced-motion`, tabla con `<caption>` y headers
  semánticos, estados de carga/error/vacío.
- `fetch` con timeout/abort y parseo RFC 7807; `sessionStorage` para la sesión.

## Mejoras sugeridas (priorizadas)

### P0 — quick wins, bajo riesgo (≈30–45 min)

1. **Login: semántica de autofill y error**
   - `web/src/pages/LoginPage.tsx` (campos email/password).
   - Cambiar `autocomplete="email"` por `autocomplete="username"` (el guide de sign-in lo
     pide para que los password managers lo reconozcan).
   - Agregar `name="email"` / `name="password"` estables, `spellcheck="false"`,
     `autocapitalize="none"` y `autocorrect="off"` en el email.
   - Agregar toggle **Mostrar/ocultar contraseña** (el guide lo recomienda explícitamente).
   - **Quitar `aria-invalid` de ambos campos cuando el error es 401**: hoy un error de
     credenciales marca los dos campos como inválidos y los asocia al mismo `aria-describedby`;
     el error es de formulario, no de campo. Dejar `role="alert"` en el mensaje y, si se
     quiere, mover el foco al alert.
   - `enterkeyhint="next"` en email y `enterkeyhint="go"` en password.

2. **Buscador: semántica de form moderno**
   - `web/src/pages/SearchPage.tsx`.
   - Envolver en `<search>` (o mantener `role="search"`, pero el elemento nativo es preferible)
     y usar `type="search"`, `name="q"`, `enterkeyhint="search"`, `autocomplete="off"` y
     `spellcheck="false"` en el campo de texto.
   - `name="ciudad"` + `autocomplete="address-level2"` en ciudad; `name` en los `<select>`.
   - Los inputs de fecha ya tienen `aria-invalid`/`aria-describedby`; mantener.

### P1 — accesibilidad/responsive (≈30–45 min)

3. **Tabla de reportes**
   - `web/src/pages/ReportsPage.tsx` + `web/src/styles.css` (`.report-table`).
   - El wrapper con `overflow-x: auto` no es scrolleable por teclado: agregar
     `tabIndex={0}`, `role="region"` y `aria-labelledby` apuntando al `caption` (darle `id`).
   - Agregar `container-type: inline-size` al wrapper y un `@container (width < 600px)` que
     convierta la tabla en tarjetas apiladas con labels (`td::before`), como indica el guide
     `responsive-table`.
   - Sticky `thead` en desktop para no perder el contexto al scrollear.

4. **Headers de seguridad en Nginx**
   - `web/nginx.conf`.
   - Agregar `Content-Security-Policy` mínima acorde al build de Vite (sin inline scripts):
     `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;
      connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'self';
      frame-ancestors 'none'`.
   - Sumar `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`,
     `Permissions-Policy: camera=(self), microphone=(), geolocation=()` y
     `X-Frame-Options: DENY`.
   - Probar login, búsqueda, checkout, tickets y acceso con la CSP puesta. No agregar HSTS en
     localhost.
   - Nota: el JWT vive en `sessionStorage`; para producción real conviene cookie `HttpOnly`
     (`__Host-` + `SameSite=Lax`), pero eso toca gateway/backend, no solo la UI.

### P2 — post-entrega (no antes del congelamiento)

5. **Navbar responsive con Popover**: en 375 px los links hacen wrap; el guide `navbar`
   propone un botón “Menú” con `popovertarget` y el mismo `<nav>` en ambos layouts. Es un
   cambio de header + JS de medición; no lo metería antes de la entrega.
6. **View transitions entre rutas**: `document.startViewTransition` o el `viewTransition` de
   React Router para un cross-fade al navegar. Es progresivo (si no hay soporte, navega
   igual), pero requiere CSS `::view-transition-*` y probar foco/scroll.
7. **Container queries en cards**: `.event-card`, `.ticket-card` y `.card` pueden adaptar su
   layout al ancho de su contenedor en vez del viewport. Cosmético.
8. **Code splitting por ruta**: `App.tsx` importa todas las páginas eagerly; con
   `React.lazy`/`Suspense` se reduce el bundle inicial (hoy 315 KB, 97 KB gzip). No es crítico
   para el TP; el bundle ya es razonable.
9. **`:user-invalid` compartido**: un pequeño hook/utility que sincronice `aria-invalid` con
   `:user-invalid` en todos los formularios. Hoy la validación local funciona; esto unifica
   el patrón de login/búsqueda/cantidad.

## Recomendación

Antes del congelamiento haría **P0 + P1 (1–1.5 h)**: son cambios acotados, no tocan el
contrato y mejoran accesibilidad/seguridad visible. P2 queda como deuda documentada
(post-entrega), salvo que sobre tiempo para el Popover del navbar.

El `legacy-scan` en 0 es una buena señal: la UI no usa patrones viejos detectables; las
mejoras son de accesibilidad, responsive y hardening, no de “código legacy”.
