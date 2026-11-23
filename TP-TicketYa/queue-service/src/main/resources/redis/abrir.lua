-- KEYS[1] hash de estado, KEYS[2] set de filas abiertas
-- ARGV[1] batchSize, ARGV[2] intervaloSegundos, ARGV[3] now millis, ARGV[4] eventoId
-- Devuelve {codigo, estado, batchSize, intervaloSegundos}.
-- codigo: 0 ya existía abierta, 1 creada, 2 reabierta tras AGOTADO.
-- Una fila cerrada por CANCELADO o FINALIZADO no se reabre.

if redis.call('EXISTS', KEYS[1]) == 1 then
  local estado = redis.call('HGET', KEYS[1], 'estado')
  local batch = redis.call('HGET', KEYS[1], 'batchSize')
  local intervalo = redis.call('HGET', KEYS[1], 'intervaloSegundos')
  if estado == 'ABIERTA' then
    redis.call('SADD', KEYS[2], ARGV[4])
    return {0, estado, batch, intervalo}
  end
  local motivo = redis.call('HGET', KEYS[1], 'motivo')
  if estado == 'CERRADA' and motivo == 'AGOTADO' then
    -- Se agregó capacidad: la fila vuelve a abrir y conserva los que esperaban.
    -- Los que ya tenían token quedaron CERRADA y vuelven a ingresar por ingresar.lua.
    redis.call('HSET', KEYS[1], 'estado', 'ABIERTA')
    redis.call('HDEL', KEYS[1], 'motivo', 'cerradaEn')
    redis.call('SADD', KEYS[2], ARGV[4])
    return {2, 'ABIERTA', batch, intervalo}
  end
  return {0, estado, batch, intervalo}
end

redis.call('HSET', KEYS[1],
        'estado', 'ABIERTA',
        'batchSize', ARGV[1],
        'intervaloSegundos', ARGV[2],
        'version', '1',
        'ultimaTanda', ARGV[3])
redis.call('SADD', KEYS[2], ARGV[4])
return {1, 'ABIERTA', ARGV[1], ARGV[2]}
