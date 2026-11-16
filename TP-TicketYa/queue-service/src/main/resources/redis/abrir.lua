-- KEYS[1] hash de estado
-- ARGV[1] batchSize, ARGV[2] intervaloSegundos
-- Devuelve {creada, estado, batchSize, intervaloSegundos}. creada es 1 o 0.

if redis.call('EXISTS', KEYS[1]) == 1 then
  return {0,
          redis.call('HGET', KEYS[1], 'estado'),
          redis.call('HGET', KEYS[1], 'batchSize'),
          redis.call('HGET', KEYS[1], 'intervaloSegundos')}
end

redis.call('HSET', KEYS[1],
        'estado', 'ABIERTA',
        'batchSize', ARGV[1],
        'intervaloSegundos', ARGV[2],
        'version', '1')
return {1, 'ABIERTA', ARGV[1], ARGV[2]}
