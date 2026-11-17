-- KEYS[1] hash de estado, KEYS[2] set de filas abiertas
-- ARGV[1] batchSize, ARGV[2] intervaloSegundos, ARGV[3] now millis, ARGV[4] eventoId
-- Devuelve {creada, estado, batchSize, intervaloSegundos}. creada es 1 o 0.
-- Una fila que ya existe no se vuelve a anunciar. Si está CERRADA, no entra a queue:open.

if redis.call('EXISTS', KEYS[1]) == 1 then
  local estado = redis.call('HGET', KEYS[1], 'estado')
  if estado == 'ABIERTA' then
    redis.call('SADD', KEYS[2], ARGV[4])
  end
  return {0,
          estado,
          redis.call('HGET', KEYS[1], 'batchSize'),
          redis.call('HGET', KEYS[1], 'intervaloSegundos')}
end

redis.call('HSET', KEYS[1],
        'estado', 'ABIERTA',
        'batchSize', ARGV[1],
        'intervaloSegundos', ARGV[2],
        'version', '1',
        'ultimaTanda', ARGV[3])
redis.call('SADD', KEYS[2], ARGV[4])
return {1, 'ABIERTA', ARGV[1], ARGV[2]}
