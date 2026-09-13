package com.livehouse.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
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
}