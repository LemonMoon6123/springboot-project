-- 分布式锁释放脚本
-- 确保只有锁的持有者才能释放锁

-- 比较线程标识与锁的标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
return 0