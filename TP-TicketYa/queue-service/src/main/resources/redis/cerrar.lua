-- KEYS[1] hash de estado, KEYS[2] set de filas abiertas, KEYS[3] hash de habilitados
-- ARGV[1] motivo, ARGV[2] now millis, ARGV[3] eventoId
-- Devuelve {codigo, motivo, batchSize, intervaloSegundos}
-- codigo: 0 no existe, 1 se cerró ahora, 2 ya estaba cerrada

if redis.call('EXISTS', KEYS[1]) == 0 then
  return {0}
end

local estado = redis.call('HGET', KEYS[1], 'estado')
local batch = redis.call('HGET', KEYS[1], 'batchSize')
local intervalo = redis.call('HGET', KEYS[1], 'intervaloSegundos')
if estado == 'CERRADA' then
  return {2, redis.call('HGET', KEYS[1], 'motivo'), batch, intervalo}
end

redis.call('HSET', KEYS[1], 'estado', 'CERRADA', 'motivo', ARGV[1], 'cerradaEn', ARGV[2])
redis.call('SREM', KEYS[2], ARGV[3])

local campos = redis.call('HGETALL', KEYS[3])
for i = 1, #campos, 2 do
  redis.call('HSET', KEYS[3], campos[i], 'CERRADA')
end

return {1, ARGV[1], batch, intervalo}
