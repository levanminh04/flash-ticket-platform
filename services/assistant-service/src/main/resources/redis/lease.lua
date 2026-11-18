if ARGV[1] == 'release' then
  -- Worker cũ không được xóa lease của worker mới.
  if redis.call('GET', KEYS[2]) == ARGV[3] then redis.call('DEL', KEYS[2]) end
  return 1
end
local stored = redis.call('GET', KEYS[1])
if not stored or redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end
local valid, state = pcall(cjson.decode, stored)
if valid and state.conversationId == ARGV[2] then return 1 end
return 0
