local stored = redis.call('GET', KEYS[1])
if not stored then return 'CONVERSATION_EXPIRED' end
local valid, state = pcall(cjson.decode, stored)
if not valid or not state.conversationId or not state.version then return 'STATE_CORRUPT' end
if state.conversationId ~= ARGV[1] then return 'CONVERSATION_CONFLICT' end
-- Replay phải phân xử trước version/quota/lease, không renew hoặc gọi model lại.
if state.lastRequestId == ARGV[3] then
  if state.lastRequestHash == ARGV[4] then return 'OK\n' .. stored end
  return 'REQUEST_ID_CONFLICT'
end
if state.version ~= ARGV[2] then return 'CONVERSATION_CONFLICT' end
if redis.call('EXISTS', KEYS[2]) == 1 then return 'CONVERSATION_BUSY' end
local attempts = redis.call('INCR', KEYS[3])
if attempts == 1 then redis.call('PEXPIRE', KEYS[3], 60000) end
if attempts > 6 then return 'RATE_LIMITED' end
redis.call('SET', KEYS[2], ARGV[5], 'PX', 60000)
return 'OK\n' .. stored
