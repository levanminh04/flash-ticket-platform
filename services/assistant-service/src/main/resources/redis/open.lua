-- Mở lại không gia hạn TTL; chỉ phiên mới dùng thời gian Redis.
local stored = redis.call('GET', KEYS[1])
if stored then return 'OK\n' .. stored end
local clock = redis.call('TIME')
local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
-- Không encode lại bằng Lua CJSON: mảng JSON rỗng phải giữ [] thay vì biến thành {}.
local encoded = string.gsub(ARGV[1], '"expiresAtEpochMillis"%s*:%s*%d+',
  '"expiresAtEpochMillis":' .. string.format('%.0f', now + 86400000), 1)
redis.call('SET', KEYS[1], encoded, 'PX', 86400000)
return 'OK\n' .. encoded
