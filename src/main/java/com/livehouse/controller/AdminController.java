package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制器 - 用于库存管理和数据修复
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    /**
     * 查看Redis库存
     */
    @GetMapping("/stock/{ticketTypeId}")
    public Result getRedisStock(@PathVariable Long ticketTypeId) {
        String stockKey = "ticket:stock:" + ticketTypeId;
        String stock = stringRedisTemplate.opsForValue().get(stockKey);
        
        log.info("查询Redis库存，票种ID：{}，当前库存：{}", ticketTypeId, stock);
        return Result.ok(stock != null ? stock : "0");
    }

    /**
     * 手动恢复Redis库存
     */
    @PostMapping("/restore-stock")
    public Result restoreRedisStock(@RequestParam Long ticketTypeId, 
                                   @RequestParam Long userId, 
                                   @RequestParam Integer quantity) {
        try {
            Long result = seckillScriptExecutor.executeRestoreStock(ticketTypeId, userId, quantity);
            
            if (result == 0) {
                log.info("手动恢复Redis库存成功，票种ID：{}，用户ID：{}，数量：{}", 
                        ticketTypeId, userId, quantity);
                return Result.ok("库存恢复成功");
            } else {
                return Result.fail("库存恢复失败，结果码：" + result);
            }
        } catch (Exception e) {
            log.error("手动恢复Redis库存失败", e);
            return Result.fail("库存恢复失败：" + e.getMessage());
        }
    }

    /**
     * 查看用户购买记录
     */
    @GetMapping("/user-order/{ticketTypeId}/{userId}")
    public Result getUserOrderCount(@PathVariable Long ticketTypeId, @PathVariable Long userId) {
        String orderKey = "ticket:order:" + ticketTypeId + ":" + userId;
        String count = stringRedisTemplate.opsForValue().get(orderKey);
        
        log.info("查询用户购买记录，票种ID：{}，用户ID：{}，购买数量：{}", 
                ticketTypeId, userId, count);
        return Result.ok(count != null ? count : "0");
    }

    /**
     * 清除用户购买记录
     */
    @DeleteMapping("/user-order/{ticketTypeId}/{userId}")
    public Result clearUserOrder(@PathVariable Long ticketTypeId, @PathVariable Long userId) {
        String orderKey = "ticket:order:" + ticketTypeId + ":" + userId;
        Boolean deleted = stringRedisTemplate.delete(orderKey);
        
        log.info("清除用户购买记录，票种ID：{}，用户ID：{}，结果：{}", 
                ticketTypeId, userId, deleted);
        return Result.ok("用户购买记录已清除");
    }

    /**
     * 清除用户待支付状态
     */
    @DeleteMapping("/user-status/{ticketTypeId}/{userId}")
    public Result clearUserStatus(@PathVariable Long ticketTypeId, @PathVariable Long userId) {
        String statusKey = "user:order:status:" + userId + ":" + ticketTypeId;
        Boolean deleted = stringRedisTemplate.delete(statusKey);
        
        log.info("清除用户待支付状态，票种ID：{}，用户ID：{}，结果：{}", 
                ticketTypeId, userId, deleted);
        return Result.ok("用户待支付状态已清除");
    }

    /**
     * 查看IP限流状态
     */
    @GetMapping("/rate-limit/{ip}")
    public Result getRateLimitStatus(@PathVariable String ip) {
        String rateLimitKey = "rate_limit:" + ip;
        Long requestCount = stringRedisTemplate.opsForZSet().count(rateLimitKey, 
                System.currentTimeMillis() - 10000, System.currentTimeMillis());
        
        log.info("查询IP限流状态，IP：{}，10秒内请求次数：{}", ip, requestCount);
        return Result.ok("IP: " + ip + ", 10秒内请求次数: " + requestCount + "/3");
    }

    /**
     * 清除IP限流记录
     */
    @DeleteMapping("/rate-limit/{ip}")
    public Result clearRateLimit(@PathVariable String ip) {
        String rateLimitKey = "rate_limit:" + ip;
        Boolean deleted = stringRedisTemplate.delete(rateLimitKey);
        
        log.info("清除IP限流记录，IP：{}，结果：{}", ip, deleted);
        return Result.ok("IP限流记录已清除");
    }

    /**
     * 查看演出核销统计
     */
    @GetMapping("/verify-stats/{showId}")
    public Result getVerifyStats(@PathVariable Long showId) {
        String bitmapKey = "ticket:verify:" + showId;
        Long verifiedCount = stringRedisTemplate.execute(
            (org.springframework.data.redis.core.RedisCallback<Long>) redisConnection -> {
                byte[] key = bitmapKey.getBytes();
                return redisConnection.bitCount(key);
            });
        
        log.info("查询演出核销统计，演出ID：{}，已核销数量：{}", showId, verifiedCount);
        return Result.ok("演出ID: " + showId + ", 已核销票数: " + verifiedCount);
    }
}