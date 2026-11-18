-- KEYS[1] sorted set de espera, KEYS[2] hash de estado, KEYS[3] secuencia, KEYS[4] habilitados
-- ARGV[1] userId, ARGV[2] epoch millis
-- Devuelve {codigo, rank, batchSize, intervaloSegundos}
-- codigo: 0 sin fila, 1 cerrada, 2 ya estaba, 3 ingreso nuevo, 4 ya habilitado

if redis.call('EXISTS', KEYS[2]) == 0 then
  return {0}
end

local estado = redis.call('HGET', KEYS[2], 'estado')
if estado ~= 'ABIERTA' then
  return {1}
end

local habilitado = redis.call('HGET', KEYS[4], ARGV[1])
if habilitado then
  local exp = string.match(habilitado, '^[^|]+|([^|]+)')
  if habilitado == 'PENDIENTE' or (exp and tonumber(exp) > tonumber(ARGV[2])) then
    return {4}
  end
  redis.call('HDEL', KEYS[4], ARGV[1])
end

local existente = redis.call('ZSCORE', KEYS[1], ARGV[1])
local codigo = 2
if not existente then
  local seq = redis.call('INCR', KEYS[3])
  local score = tonumber(ARGV[2]) + (seq / 1000000)
  local agregado = redis.call('ZADD', KEYS[1], 'NX', score, ARGV[1])
  if agregado == 1 then
    codigo = 3
  end
end

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
local batch = redis.call('HGET', KEYS[2], 'batchSize')
local intervalo = redis.call('HGET', KEYS[2], 'intervaloSegundos')
return {codigo, rank, batch, intervalo}
