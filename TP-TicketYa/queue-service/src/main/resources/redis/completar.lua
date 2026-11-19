-- KEYS[1] hash de habilitados
-- ARGV[1] userId, ARGV[2] valor jti|expMillis|jwt
-- Devuelve {1} si este llamado ganó el PENDIENTE, {0} si ya tenía token.

if redis.call('HGET', KEYS[1], ARGV[1]) ~= 'PENDIENTE' then
  return {0}
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
return {1}
