package com.livehouse.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;

/**
 * 限流脚本执行器
 */
@Slf4j
@Component
public class RateLimitExecutor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    @PostConstruct
    public void init() {
        // 初始化限流Lua脚本
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setLocation(new ClassPathResource("rate_limit.lua"));
        rateLimitScript.setResultType(Long.class);
        
        log.info("限流Lua脚本初始化完成");
    }

    /**
     * 执行限流检查
     * 
     * @param ip IP地址
     * @param windowSizeSeconds 时间窗口大小（秒）
     * @param limit 限流阈值
     * @return true-允许通过，false-超出限制
     */
    public boolean checkRateLimit(String ip, int windowSizeSeconds, int limit) {
        try {
            // 1. 获取当前时间的毫秒值
            long currentTime = System.currentTimeMillis();
            
            // 2. 执行Lua脚本
            Long result = stringRedisTemplate.execute(
                rateLimitScript,
                Collections.emptyList(),
                ip,
                String.valueOf(windowSizeSeconds),
                String.valueOf(limit),
                String.valueOf(currentTime)
            );
            
            boolean allowed = result != null && result == 0;
            
            if (!allowed) {
                log.warn("IP限流触发，IP：{}，窗口：{}秒，限制：{}次", ip, windowSizeSeconds, limit);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("执行限流脚本失败，IP：{}", ip, e);
            // 发生异常时，默认允许通过
            return true;
        }
    }
}