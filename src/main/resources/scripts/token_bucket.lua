-- KEYS[1] = bucket key
-- ARGV[1] = capacity
-- ARGV[2] = refill_tokens
-- ARGV[3] = refill_period_seconds
-- ARGV[4] = now (epoch millis)
-- ARGV[5] = requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_tokens = tonumber(ARGV[2])
local refill_period = tonumber(ARGV[3]) * 1000  -- convert to millis
local now = tonumber(ARGV[4])
local requested = tonumber(ARGV[5])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    -- first request ever for this key
    tokens = capacity
    last_refill = now
end

-- Calculate how many refill cycles have elapsed and top up tokens accordingly
local elapsed = now - last_refill
if elapsed > 0 then
    local refill_cycles = math.floor(elapsed / refill_period)
    if refill_cycles > 0 then
        tokens = math.min(capacity, tokens + (refill_cycles * refill_tokens))
        last_refill = last_refill + (refill_cycles * refill_period)
    end
end

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
-- TTL safety: expire the bucket key if unused for 2x the refill period,
-- so idle keys don't accumulate in Redis forever
redis.call('PEXPIRE', key, refill_period * 2)

return {allowed, tokens}