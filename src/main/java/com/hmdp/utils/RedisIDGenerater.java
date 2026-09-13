package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Auther: shaolei
 * @Date: 2026/9/5-16:32
 * @Description：基于Redis的String数据结构的自增特性实现全局ID生成器。
 */
@Component
public class RedisIDGenerater {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 起始时间 -> 202601010秒
    private static final long BEGIN_TIMESTAMP = 1767229260L;
    private static final int COUNT_BITS = 32;
    public long getId(String keyPrefix){
        // 1.构造时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        // 2.构造序列号
        // 2.1 获取当前日期，年月日。
        String date = now.format(DateTimeFormatter.ofPattern("YYYY:MM:dd"));
        // 2.2 使用string中值自增（+1）方法，保持序列号的递增，并用日期区分，每天换一个key,保证单日序列号不会超出最大位数限制。
        // 也方便对数据进行统计。
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接返回
        return timestamp << COUNT_BITS | count; // COUNT_BITS为32时，timestamp就31位。
    }
}
