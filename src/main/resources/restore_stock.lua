-- 归还库存 Lua 脚本
-- 功能：原子性地归还库存并减少用户购买记录

-- 1. 参数定义
local ticketTypeId = ARGV[1]     -- 票种ID
local userId = ARGV[2]           -- 用户ID  
local quantity = tonumber(ARGV[3]) or 1  -- 归还数量，默认为1

-- 2. Redis key 定义
local stockKey = 'ticket:stock:' .. ticketTypeId              -- 库存key
local orderKey = 'ticket:order:' .. ticketTypeId .. ':' .. userId  -- 用户购买记录key

-- 3. 归还库存
redis.call('incrby', stockKey, quantity)

-- 4. 减少用户购买记录
local currentCount = tonumber(redis.call('decrby', orderKey, quantity) or '0')

-- 5. 如果用户购买记录为0或负数，删除记录
if currentCount <= 0 then
    redis.call('del', orderKey)
end

-- 6. 返回成功
return 0