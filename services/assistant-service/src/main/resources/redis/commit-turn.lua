local stored = redis.call('GET', KEYS[1])
if not stored then return 'CONVERSATION_EXPIRED' end
local valid, state = pcall(cjson.decode, stored)
if not valid or not state.conversationId or not state.version then return 'STATE_CORRUPT' end
if state.conversationId ~= ARGV[1] or state.version ~= ARGV[2] then return 'CONVERSATION_CONFLICT' end
if redis.call('GET', KEYS[2]) ~= ARGV[3] then return 'LEASE_LOST' end
local clock = redis.call('TIME')
local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
-- Giữ nguyên JSON do Jackson tạo, bao gồm [] và protocol metadata.
local encoded = string.gsub(ARGV[4], '"expiresAtEpochMillis"%s*:%s*%d+',
  '"expiresAtEpochMillis":' .. string.format('%.0f', now + 86400000), 1)
-- Context/history/replay/version/TTL cùng commit; không upsert state đã hết hạn.
redis.call('SET', KEYS[1], encoded, 'PX', 86400000)
redis.call('DEL', KEYS[2])
return 'OK\n' .. encoded
