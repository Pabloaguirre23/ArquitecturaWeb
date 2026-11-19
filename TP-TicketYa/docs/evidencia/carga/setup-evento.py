import json
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

BASE = "http://localhost:8080"


def call(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        raise SystemExit(f"{method} {path} -> {error.code}: {raw}")


def login(email):
    status, body = call("POST", "/api/auth/login", body={"email": email, "password": "demo"})
    assert status == 200, status
    return body["token"]


admin = login("admin@ticketya.test")
organizer = login("organizador@ticketya.test")

sufijo = uuid.uuid4().hex[:8]
status, lugar = call("POST", "/api/catalog/venues", admin, {
    "nombre": f"Carga {sufijo}",
    "direccion": "Calle de carga 1",
    "ciudad": "Tandil",
    "latitud": -37.3217,
    "longitud": -59.1332,
})
assert status == 201, status
lugar_id = lugar["id"]

status, sector = call("POST", f"/api/catalog/venues/{lugar_id}/sectors", admin, {
    "nombre": f"Campo {sufijo}",
    "tipo": "NO_NUMERADO",
    "capacidad": 2000,
})
assert status == 201, status
sector_id = sector["id"]

fecha = (datetime.now(timezone.utc) + timedelta(days=30)).replace(microsecond=0).isoformat()
status, evento = call("POST", "/api/catalog/events", organizer, {
    "nombre": f"Evento de carga {sufijo}",
    "descripcion": "Evento para la prueba de carga de la Entrega 2",
    "tipo": "RECITAL",
    "lugarId": lugar_id,
    "fecha": fecha,
    "participantes": ["Artista de carga"],
    "limitePorCliente": 1000,
    "altaDemanda": False,
})
assert status == 201, status
evento_id = evento["id"]

status, _ = call("PUT", f"/api/catalog/events/{evento_id}/prices/{sector_id}", organizer, {"precio": 12000})
assert status == 200, status

status, publicado = call("POST", f"/api/catalog/events/{evento_id}/publish", organizer)
assert status == 200 and publicado["estado"] == "PUBLICADO", (status, publicado)

print(json.dumps({"lugarId": lugar_id, "sectorId": sector_id, "eventoId": evento_id}))
