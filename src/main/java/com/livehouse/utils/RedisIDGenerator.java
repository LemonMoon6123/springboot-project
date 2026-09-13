package com.livehouse.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于Redis的全局ID生成器
 * 使用时间戳 + 序列号的方式生成全局唯一的递增式ID
 */
@Component
public class RedisIDGenerator {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 起始时间戳（2026-01-01 00:00:00 UTC的秒数）
    private static final long BEGIN_TIMESTAMP = 1767225600L;
    // 序列号位数
    private static final int COUNT_BITS = 32;
    
    /**
     * 生成全局唯一ID
     * @param keyPrefix key前缀，如"order"、"ticket"等
     * @return 全局唯一ID
     */
    public long getId(String keyPrefix){
        // 1.生成时间戳部分
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        
        // 2.生成序列号部分
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        
        // 3.拼接时间戳和序列号返回
        return timestamp << COUNT_BITS | count;
    }
}