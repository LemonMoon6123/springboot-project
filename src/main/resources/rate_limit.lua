-- IP限流 Lua 脚本 (滑动窗口)
-- 功能：限制同一IP在指定时间窗口内的请求次数
-- ZSet 结构说明：这里用 ZSet 存储请求记录，每个元素的 score（分数）和 member（成员值）都设置为请求时间戳。
-- score 用于时间排序和范围筛选，member 用时间戳是为了保证元素唯一。
-- 每次请求都会写入 ZSet，请求量极大时会占用较多内存，适合中小流量的限流场景。

-- 1. 参数定义
local ip = ARGV[1]              -- IP地址
local windowSize = tonumber(ARGV[2])  -- 时间窗口大小（秒）
local limit = tonumber(ARGV[3])       -- 限流阈值
local currentTime = tonumber(ARGV[4]) -- 当前时间戳（毫秒）

-- 2. Redis key 定义
local rateLimitKey = 'rate_limit:' .. ip

-- 3. 清理过期的请求记录（滑动窗口） expireTime为窗口的左边界，currentTime是窗口的右边界
local expireTime = currentTime - (windowSize * 1000)
-- 删掉所有时间戳 ≤ expireTime 的请求记录，也就是所有滑出窗口的过期请求。
-- （ZSet命令）zremrangebyscore key min max： 删除集合中 score 在 [min, max] 区间内的所有元素。-inf是负无穷，从最早的一条记录开始删
redis.call('zremrangebyscore', rateLimitKey, '-inf', expireTime)


-- 4. 获取当前窗口内的请求次数
-- zcard key：Redis ZSet 命令，返回有序集合中元素的总数量，时间复杂度为 O (1)，底层有内置计数器直接读取。
-- 因为上一步已经删除了所有过期记录，所以此时集合中的元素数量，就是当前时间窗口内该 IP 的总请求次数。
local currentCount = redis.call('zcard', rateLimitKey)

-- 5. 检查是否超出限制
if currentCount >= limit then
    -- 设置过期时间，当这个 IP 再也不发请求了，自动删掉键，避免 Redis 内存泄漏
    redis.call('expire', rateLimitKey, windowSize)
    return 1  -- 超出限制
end

-- 6. 记录本次请求
-- zadd key score member：Redis ZSet 命令，向有序集合中添加一个元素，并指定其分数，ZSet 会自动按 score 从小到大排序。
-- 第一个 currentTime 是 score（分数），用于排序和时间范围筛选，第二个 currentTime 是 member（成员值），这里直接用时间戳作为成员标识。
redis.call('zadd', rateLimitKey, currentTime, currentTime)

-- 7. 设置过期时间，每次成功请求后，都刷新一次键的过期时间，保证键的存活时间始终等于一个窗口大小。
-- 给键设置 windowSize 秒的过期时间，刚好等于「最后一条记录自然失效的时间」。
-- 即：每次请求都刷新 expire，本质就是把键的过期时间，始终对齐到「当前最新请求的失效时间」。
-- 只要这个 IP 一直在发请求，键就永远不会提前消失；只要它停发请求满一个窗口时长，键就自动清理。
redis.call('expire', rateLimitKey, windowSize)

-- 8. 返回成功
return 0  -- 请求通过