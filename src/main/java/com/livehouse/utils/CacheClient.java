package com.livehouse.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.livehouse.utils.RedisConstants.*;

/**
 * Redis缓存工具类
 * 封装缓存穿透、缓存击穿、缓存雪崩等问题的解决方案
 */
@Slf4j
@Component
public class CacheClient {
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 创建线程池用于异步缓存重建
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    /**
     * 设置缓存（带随机过期时间，防止缓存雪崩）
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), 
            time + RandomUtil.randomLong(0, 10), unit);
    }

    /**
     * 设置带逻辑过期时间的缓存
     */
    public void setWithLogicExpireTime(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(unit.toMinutes(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透问题解决方案
     * 使用空值缓存防止缓存穿透
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,
            Long time, TimeUnit unit){
        
        // 1.从Redis查询缓存
        String key = keyPrefix + id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);

        // 2.如果缓存命中且不为空，直接返回
        if (StrUtil.isNotBlank(jsonData)) {
            return JSONUtil.toBean(jsonData, type);
        }

        // 3.如果命中的是空值（防穿透的空缓存），返回null
        if(jsonData != null){
            return null;
        }

        // 4.缓存未命中，查询数据库
        R r = dbFallback.apply(id);
        
        // 5.数据库也没有，缓存空值防止缓存穿透
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        
        // 6.数据库有数据，写入缓存
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 缓存击穿问题解决方案 - 互斥锁
     */
    public <R,ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,
            Long time, TimeUnit unit){
        
        String key = keyPrefix + id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);

        // 缓存命中
        if (StrUtil.isNotBlank(jsonData)) {
            return JSONUtil.toBean(jsonData, type);
        }

        // 命中空值
        if(jsonData != null){
            return null;
        }

        // 缓存未命中，尝试获取互斥锁
        String lockKey = LOCK_KEY + id;
        R r = null;
        try{
            boolean isGetLock = tryLock(lockKey);
            
            // 未获取到锁，休眠后重试
            if(!isGetLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            
            // 获取到锁，查询数据库
            r = dbFallback.apply(id);

            // 数据库无数据，缓存空值
            if(r == null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            
            // 重建缓存
            this.set(key, r, time, unit);
            
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            // 释放锁
            unLock(lockKey);
        }
        return r;
    }

    /**
     * 缓存击穿问题解决方案 - 逻辑过期
     * 前提：需提前将热点数据，也就是演出信息数据缓存到redis中，也就是需做缓存预热，否则无效。
     */
    public <R,ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, 
                                         Function<ID,R> dbFallback, Long time, TimeUnit unit){
        
        String key = keyPrefix + id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);

        // 缓存未命中，说明不是热点数据
        if (StrUtil.isBlank(jsonData)) {
            return null;
        }

        // 命中，检查逻辑过期时间
        RedisData redisData = JSONUtil.toBean(jsonData, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        
        // 未过期，直接返回
        if(expireTime.isAfter(LocalDateTime.now())){
            return JSONUtil.toBean((JSONObject) redisData.getData(), type);
        }

        // 已过期，尝试获取互斥锁进行缓存重建
        String lockKey = LOCK_KEY + id;
        boolean isGetLock = tryLock(lockKey);
        
        if(isGetLock) {
            // 获取到锁，异步重建缓存
            EXECUTOR_SERVICE.submit(() -> {
                try {
                    R r = dbFallback.apply(id);
                    this.setWithLogicExpireTime(key, r, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        
        // 无论是哪个线程来，都返回旧数据
        return JSONUtil.toBean((JSONObject) redisData.getData(), type);
    }

    /**
     * 尝试获取分布式锁
     */
    public boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    /**
     * 释放分布式锁
     */
    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 双写一致性问题解决方案 - Redisson读写锁（读路径）
     * 读锁共享：多个读线程可以同时查缓存；读写互斥，保证读期间没有线程在更新，读到的一定是最新数据。
     * 流程：读锁内只查缓存 -> 未命中则竞争写锁 -> 双检缓存 -> 第一个拿锁的线程查库回填，其余直接读缓存
     */
    public <R, ID> R queryWithReadWriteLock(
            String lockKeyPrefix, String cacheKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKeyPrefix + id);
        String key = cacheKeyPrefix + id;

        // 1.读锁内只查缓存（读锁共享，多线程并发读不互斥）
        RLock readLock = readWriteLock.readLock();
        readLock.lock();
        String jsonData;
        try {
            jsonData = stringRedisTemplate.opsForValue().get(key);
        } finally {
            readLock.unlock();
        }

        // 缓存命中，直接返回
        if (StrUtil.isNotBlank(jsonData)) {
            return JSONUtil.toBean(jsonData, type);
        }
        // 命中空值缓存（布隆误判时的兜底），返回null
        if (jsonData != null) {
            return null;
        }

        // 2.缓存未命中，竞争写锁重建缓存（写锁互斥，避免并发线程都打到数据库）
        RLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            // 双检：拿到写锁后再查一次缓存，前面排队的线程已重建的话直接返回
            jsonData = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(jsonData)) {
                return JSONUtil.toBean(jsonData, type);
            }
            if (jsonData != null) {
                return null;
            }

            // 3.查数据库
            R r = dbFallback.apply(id);

            // 数据库也没有，缓存空值（短TTL），防止缓存穿透
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 回填缓存（带随机TTL，顺带防止缓存雪崩）
            this.set(key, r, time, unit);
            return r;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 双写一致性问题解决方案 - Redisson读写锁（写路径）
     * 写锁独占：加写锁期间所有读锁和其他写锁都会被阻塞。
     * 流程：加写锁 -> 更新数据库 -> 删除缓存 -> 释放写锁（下次查询时由读路径回填最新数据）
     */
    public void updateWithReadWriteLock(
            String lockKeyPrefix, String cacheKeyPrefix, Object id, Runnable dbUpdate) {

        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKeyPrefix + id);
        RLock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            // 1.更新数据库
            dbUpdate.run();
            // 2.删除缓存
            stringRedisTemplate.delete(cacheKeyPrefix + id);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 缓存穿透问题解决方案 - Redisson布隆过滤器
     * 首次获取时自动初始化（预计放入10000个元素，误判率5%），重复调用tryInit是幂等的，不影响使用。
     * 底层把位数组存在 Redis 的 Bitmap 中，通过 SETBIT / GETBIT 操作位，每个哈希函数的结果映射到一个位偏移量，多个位同时为1时才可能是存在。
     * 不支持删除元素；误判率随插入数量增加而上升
     */
    public RBloomFilter<String> getBloomFilter(String key) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(key);
        // 如果该布隆过滤器在 Redis 中尚未初始化，它会根据这两个参数计算所需的位数组大小和哈希函数个数，并在 Redis 中创建对应的 Bitmap。
        bloomFilter.tryInit(10000L, 0.05);
        return bloomFilter;
    }
}