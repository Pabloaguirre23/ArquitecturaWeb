-- KEYS[1] sorted set de espera, KEYS[2] hash de estado, KEYS[3] hash de habilitados
-- ARGV[1] now millis
-- Devuelve {codigo, ...userIds}
-- codigo: 0 fila no abierta, 1 todavía no cumple el intervalo, 2 intervalo cumplido sin clientes, 3 habilitados

local estado = redis.call('HGET', KEYS[2], 'estado')
if estado ~= 'ABIERTA' then
  return {0}
end

local ultima = tonumber(redis.call('HGET', KEYS[2], 'ultimaTanda') or '0')
local intervalo = tonumber(redis.call('HGET', KEYS[2], 'intervaloSegundos'))
local ahora = tonumber(ARGV[1])
if ahora < ultima + (intervalo * 1000) then
  return {1}
end

local batch = tonumber(redis.call('HGET', KEYS[2], 'batchSize'))
local members = redis.call('ZRANGE', KEYS[1], 0, batch - 1)
redis.call('HSET', KEYS[2], 'ultimaTanda', ARGV[1])
if #members == 0 then
  return {2}
end

local result = {3}
for _, member in ipairs(members) do
  redis.call('ZREM', KEYS[1], member)
  redis.call('HSET', KEYS[3], member, 'PENDIENTE')
  result[#result + 1] = member
end
return result
