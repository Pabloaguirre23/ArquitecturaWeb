# Colecciones Postman

| Archivo | Estado | Uso |
|---|---|---|
| `TicketYa.entrega2.postman_collection.json` | **Vigente** | Flujo con JWT: Auth, Catálogo, Inventario, Fila, Órdenes, Acceso, Reportes, Preferencias, Recomendaciones, Notificaciones y Seguridad. |
| `TicketYa.local.postman_environment.json` | Vigente | Variables del entorno local (`baseUrl`, IDs demo). Completar `tokenClient`/`tokenOrganizer`/`tokenAccess` corriendo **Auth**. |
| `TicketYa.postman_collection.json` | Histórica (Entrega 1, pre-JWT) | Se conserva como referencia. Usa `X-User-*` manuales, que el gateway descarta: esas requests responden 401. |

## Cómo correr la colección vigente

1. Levantar el stack integral y esperar los healthchecks:

   ```bash
   docker compose -f docker-compose.yml up -d --build
   docker compose -f docker-compose.yml ps
   ```

2. Importar la colección y el environment. Correr **Auth** (los cuatro logins guardan
   `tokenAdmin`, `tokenOrganizer`, `tokenClient` y `tokenAccess`).
3. Correr **Catálogo** (guarda `eventoId` y `sectorId` del seed), **Inventario** (guarda
   `reservaId`) y **Órdenes** (guarda `ordenId` y `qrCode`). Después **Acceso**, **Reportes**,
   **Preferencias**, **Recomendaciones** y **Notificaciones**.
4. **Fila** llama directo a `queue-service` (`{{queueUrl}}`) porque abrir/cerrar la fila es
   interno y no se publica por el gateway. El evento del seed no es de alta demanda; para
   probar la fila, marcar uno como `altaDemanda` o abrir la fila con el token interno.

## Newman

```bash
newman run postman/TicketYa.entrega2.postman_collection.json \
  -e postman/TicketYa.local.postman_environment.json \
  --folder Auth --folder Catálogo --folder Inventario --folder Órdenes
```

La corrida de referencia de la entrega es la suite E2E de Java (`integration-tests`), que
levanta el stack y verifica login, búsqueda, fila, compra, QR, reporte, recomendación y
notificación con JUnit. Postman queda como herramienta manual de exploración.
