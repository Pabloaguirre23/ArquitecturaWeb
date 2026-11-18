-- KEYS[1] hash de habilitados. Valor: jti|expMillis|jwt
-- ARGV[1] now millis
-- Borra los tokens vencidos y devuelve cuántos sacó.

local ahora = tonumber(ARGV[1])
local borrados = 0
local campos = redis.call('HGETALL', KEYS[1])
for i = 1, #campos, 2 do
  local exp = string.match(campos[i + 1], '^[^|]+|([^|]+)')
  if exp and tonumber(exp) <= ahora then
    redis.call('HDEL', KEYS[1], campos[i])
    borrados = borrados + 1
  end
end
return borrados
