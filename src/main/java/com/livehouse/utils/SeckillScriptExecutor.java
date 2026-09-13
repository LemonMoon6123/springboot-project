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
 * 秒杀脚本执行器
 * 封装Lua脚本的执行逻辑
 */
@Slf4j
@Component
public class SeckillScriptExecutor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> seckillScript;

    @PostConstruct
    public void init() {
        // 初始化Lua脚本
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("seckill_ticket.lua"));
        seckillScript.setResultType(Long.class);
        
        log.info("秒杀Lua脚本初始化完成");
    }

    /**
     * 执行秒杀脚本
     * 
     * @param ticketTypeId 票种ID
     * @param userId 用户ID
     * @param quantity 购买数量
     * @param limitPerUser 每人限购数量
     * @return 执行结果码：0-成功，1-库存不足，2-超出限购
     */
    public SeckillResult executeSeckill(Long ticketTypeId, Long userId, Integer quantity, Integer limitPerUser) {
        try {
            // 执行Lua脚本
            Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.emptyList(),
                ticketTypeId.toString(),
                userId.toString(),
                quantity.toString(),
                limitPerUser.toString()
            );
            
            // 解析返回结果
            return SeckillResult.fromCode(result);
            
        } catch (Exception e) {
            log.error("执行秒杀脚本失败，票种ID：{}，用户ID：{}", ticketTypeId, userId, e);
            return SeckillResult.SYSTEM_ERROR;
        }
    }

    /**
     * 秒杀结果枚举
     */
    public enum SeckillResult {
        SUCCESS(0L, "秒杀成功！"),
        INSUFFICIENT_STOCK(1L, "库存不足啦！"),
        EXCEED_LIMIT(2L, "超出限购数量啦！"),
        SYSTEM_ERROR(-1L, "系统异常！");

        private final Long code;
        private final String message;

        SeckillResult(Long code, String message) {
            this.code = code;
            this.message = message;
        }

        public Long getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }

        public static SeckillResult fromCode(Long code) {
            if (code == null) {
                return SYSTEM_ERROR;
            }
            
            for (SeckillResult result : values()) {
                if (result.code.equals(code)) {
                    return result;
                }
            }
            return SYSTEM_ERROR;
        }
    }
}