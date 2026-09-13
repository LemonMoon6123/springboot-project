-- 票务秒杀 Lua 脚本
-- 功能：原子性地进行库存校验、用户限购校验、库存扣减

-- 1. 参数定义
local ticketTypeId = ARGV[1]     -- 票种ID
local userId = ARGV[2]           -- 用户ID  
local quantity = tonumber(ARGV[3]) or 1  -- 购买数量，默认为1
local limitPerUser = tonumber(ARGV[4])   -- 每人限购数量

-- 2. Redis key 定义
local stockKey = 'ticket:stock:' .. ticketTypeId              -- 库存key
local orderKey = 'ticket:order:' .. ticketTypeId .. ':' .. userId  -- 用户购买记录key

-- 3. 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey) or '0')
if stock < quantity then
    return 1 -- 库存不足
end

-- 4. 判断用户是否已达限购数量
local buyCount = tonumber(redis.call('get', orderKey) or '0')
if buyCount + quantity > limitPerUser then
    return 2 -- 超出限购
end

-- 5. 扣减库存并记录用户购买数量
redis.call('decrby', stockKey, quantity)
redis.call('incrby', orderKey, quantity) -- Redis会自动创建这个key(当key不存在时)

-- 6. 设置用户购买记录的过期时间（24小时，防止数据堆积）
redis.call('expire', orderKey, 86400)

-- 7. 返回成功
return 0