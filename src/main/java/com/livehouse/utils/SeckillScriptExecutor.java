package com.livehouse.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

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
    private DefaultRedisScript<Long> restoreStockScript;

    @PostConstruct
    public void init() {
        // 初始化秒杀Lua脚本
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("seckill_ticket.lua"));
        seckillScript.setResultType(Long.class);
        
        // 初始化归还库存Lua脚本
        restoreStockScript = new DefaultRedisScript<>();
        restoreStockScript.setLocation(new ClassPathResource("restore_stock.lua"));
        restoreStockScript.setResultType(Long.class);
        
        log.info("Lua脚本初始化完成");
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
     * 执行归还库存脚本
     * 
     * @param ticketTypeId 票种ID
     * @param userId 用户ID
     * @param quantity 归还数量
     * @return 执行结果码：0-成功
     */
    public Long executeRestoreStock(Long ticketTypeId, Long userId, Integer quantity) {
        try {
            // 执行Lua脚本
            Long result = stringRedisTemplate.execute(
                restoreStockScript,
                Collections.emptyList(),
                ticketTypeId.toString(),
                userId.toString(),
                quantity.toString()
            );
            
            log.info("归还库存成功，票种ID：{}，用户ID：{}，数量：{}", ticketTypeId, userId, quantity);
            return result != null ? result : 0L;
            
        } catch (Exception e) {
            log.error("执行归还库存脚本失败，票种ID：{}，用户ID：{}", ticketTypeId, userId, e);
            return -1L;
        }
    }

    /**
     * 幂等地执行归还库存脚本。
     *
     * 背景：秒杀订单消费者可能因为框架内部重试（同一条消息被本地方法多次调用）、
     * broker ack丢失导致的重复投递、以及死信队列兜底消费者的介入，
     * 对"同一次抢购"触发多次归还操作。如果每次都直接调用 {@link #executeRestoreStock}，
     * 库存会被错误地多次归还，造成Redis库存虚高、与数据库库存产生偏差。
     *
     * 这里用 Redis SETNX 对 requestId 做一次性占位：无论这个方法被调用多少次，
     * 真正的Lua归还脚本只会被执行一次，后续重复调用直接跳过，天然具备幂等性。
     *
     * @param requestId    幂等请求ID（一次成功的抢购/一次退票记录对应一个requestId）
     * @param redisKeyPrefix 幂等占位key前缀，秒杀场景/退票场景分别使用不同前缀，避免key冲突
     * @param ticketTypeId 票种ID
     * @param userId       用户ID
     * @param quantity     归还数量
     * @return true-本次调用真正执行了归还；false-命中幂等，跳过归还
     */
    public boolean executeRestoreStockIdempotent(String requestId, String redisKeyPrefix,
                                                  Long ticketTypeId, Long userId, Integer quantity) {
        if (requestId == null || requestId.isEmpty()) {
            // 没有requestId（理论上不应该出现），退化为直接执行，保证兜底可用
            log.warn("归还库存缺少requestId，跳过幂等校验直接执行，票种ID：{}，用户ID：{}", ticketTypeId, userId);
            executeRestoreStock(ticketTypeId, userId, quantity);
            return true;
        }

        String idemKey = redisKeyPrefix + requestId;
        Boolean firstTime = stringRedisTemplate.opsForValue()
                .setIfAbsent(idemKey, "1", RedisConstants.SECKILL_IDEMPOTENT_TTL, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(firstTime)) {
            log.info("库存归还命中幂等标记，跳过重复归还，requestId：{}", requestId);
            return false;
        }

        Long result = executeRestoreStock(ticketTypeId, userId, quantity);
        if (result == null || result != 0L) {
            // 归还失败：释放占位标记，允许后续重试真正执行归还
            stringRedisTemplate.delete(idemKey);
            log.error("幂等归还库存执行失败，requestId：{}，结果码：{}", requestId, result);
        }
        return true;
    }

    /**
     * 将 Redis 票种库存强制校准为数据库 left_stock。
     * <p>
     * 用于「Lua 已扣 Redis、但 MySQL CAS 扣减失败」等 Redis 相对 DB 虚高的场景：
     * 不能简单 incrby 本次购买数量（会维持虚高），而应以 DB 为真相源覆盖 Redis。
     */
    public void syncRedisStockFromDb(Long ticketTypeId, Integer dbLeftStock) {
        String stockKey = RedisConstants.TICKET_STOCK_KEY + ticketTypeId;
        int stock = dbLeftStock == null ? 0 : Math.max(0, dbLeftStock);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        log.warn("Redis库存已校准为数据库库存，票种ID：{}，校准后库存：{}", ticketTypeId, stock);
    }

    /**
     * 回补用户限购计数（不改动票种库存 key）。
     */
    public void restoreUserBuyCount(Long ticketTypeId, Long userId, Integer quantity) {
        String orderKey = RedisConstants.TICKET_ORDER_KEY + ticketTypeId + ":" + userId;
        Long current = stringRedisTemplate.opsForValue().increment(orderKey, -quantity);
        if (current != null && current <= 0) {
            stringRedisTemplate.delete(orderKey);
        }
        log.info("已回补用户限购计数，票种ID：{}，用户ID：{}，数量：{}", ticketTypeId, userId, quantity);
    }

    /**
     * 秒杀结果枚举
     */
    public enum SeckillResult {
        SUCCESS(0L, "抢购成功！"),
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