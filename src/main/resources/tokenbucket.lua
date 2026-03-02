-- 令牌桶限流脚本
-- KEYS[1]: 限流的key（如 "rate:limit:seckill"）
-- ARGV[1]: 令牌桶容量（最大突发流量）
-- ARGV[2]: 每秒产生的令牌数（平均速率）
-- ARGV[3]: 当前时间戳（毫秒）
-- ARGV[4]: 每次请求消耗的令牌数（通常是1）

local key = KEYS[1]
local capacity = tonumber(ARGV[1])      -- 桶容量
local rate = tonumber(ARGV[2])          -- 每秒速率
local now = tonumber(ARGV[3])           -- 当前时间
local requested = tonumber(ARGV[4])     -- 请求令牌数

-- 获取桶中当前的令牌数和最后刷新时间
local bucket = redis.call('hmget', key, 'tokens', 'lastRefillTime')
local currentTokens = tonumber(bucket[1]) or capacity  -- 初始为满桶
local lastRefillTime = tonumber(bucket[2]) or now

-- 计算需要补充的令牌数
local elapsedTime = math.max(0, now - lastRefillTime)  -- 过去多少毫秒
local refillTokens = (elapsedTime / 1000) * rate       -- 这段时间应补充的令牌

-- 更新当前令牌数（不能超过容量）
currentTokens = math.min(capacity, currentTokens + refillTokens)

local newTokens = currentTokens
local allowed = 0  -- 0: 拒绝, 1: 通过

-- 判断是否足够
if currentTokens >= requested then
    newTokens = currentTokens - requested
    allowed = 1
end

-- 保存新状态到 Redis
redis.call('hmset', key, 'tokens', newTokens, 'lastRefillTime', now)

-- 设置过期时间（避免key长期占用内存）
redis.call('expire', key, 10)

return allowed