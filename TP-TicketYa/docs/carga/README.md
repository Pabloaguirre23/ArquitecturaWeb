# Carga del flujo de compra

Plan JMeter de login, búsqueda, reserva y pago. Entra por el gateway (`http://localhost:8080`).

Cada usuario del CSV es un cliente distinto. Reserva un lugar del campo del seed `Festival de Rock Tandil 2027` (`d0000000-0000-4000-8000-000000000001`, sector `b0000000-0000-4000-8000-000000000002`). Ese sector tiene capacidad 100: a partir de ahí la reserva responde `409` y el pago no encuentra una reserva nueva. El login (`POST /api/auth/login`) forma parte del plan; hasta que exista el endpoint, esa muestra queda en 404 y el resto sigue con `X-User-Id` y `X-User-Role`. El pago llama a inventory por `/internal/**`: hasta que orders envíe `X-Internal-Token`, esa muestra queda en 502. El campo del seed tiene 100 lugares, así que el resto de las reservas responde 409.

## Datos

`usuarios.csv` tiene 1000 filas: `userId`, `email`, `password`. La contraseña de prueba es `ticketya-dev`.

## Comando

Desde esta carpeta, con el gateway, catalog, inventory y orders levantados:

```bash
jmeter -n -t compra.jmx -Jusers=500 -Jramp=60 -Jhost=localhost -Jport=8080 -Jusuarios=usuarios.csv -l resultados/compra-500.jtl -j resultados/jmeter-500.log
jmeter -n -t compra.jmx -Jusers=1000 -Jramp=60 -Jhost=localhost -Jport=8080 -Jusuarios=usuarios.csv -l resultados/compra-1000.jtl -j resultados/jmeter-1000.log
```

`-Jusers` es la cantidad de hilos. `-Jramp` es el ramp-up en segundos. Cada hilo hace el flujo una vez. Los `.jtl` son el resultado crudo (CSV de JMeter: tiempo, código, éxito, hilo).

Corridas del 17/11/2026 contra el gateway local, ramp-up de 60 s:

| Archivo | Usuarios | Muestras | Errores |
|---|---|---|---|
| `resultados/compra-500.jtl` | 500 | 2000 | 1401 (70 %) |
| `resultados/compra-1000.jtl` | 1000 | 4000 | 3000 (75 %) |
