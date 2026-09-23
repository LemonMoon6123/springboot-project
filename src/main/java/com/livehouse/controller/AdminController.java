package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.service.IDataScreenService;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
    
    @Autowired
    private IDataScreenService dataScreenService;

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

    // ================== 数据大屏相关接口 ==================

    /**
     * 获取数据大屏总览数据
     */
    @GetMapping("/data-screen/overview")
    public Result getDataScreenOverview() {
        try {
            Map<String, Object> overview = dataScreenService.getOverallStats();
            log.info("数据大屏总览数据获取成功");
            return Result.ok(overview);
        } catch (Exception e) {
            log.error("获取数据大屏总览数据失败", e);
            return Result.fail("获取数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取营收分析数据
     */
    @GetMapping("/data-screen/revenue")
    public Result getRevenueAnalysis() {
        try {
            Map<String, Object> revenue = dataScreenService.getRevenueAnalysis();
            log.info("营收分析数据获取成功");
            return Result.ok(revenue);
        } catch (Exception e) {
            log.error("获取营收分析数据失败", e);
            return Result.fail("获取营收数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取演出售票统计
     */
    @GetMapping("/data-screen/shows")
    public Result getShowTicketStats() {
        try {
            Map<String, Object> shows = dataScreenService.getShowTicketStats();
            log.info("演出售票统计获取成功");
            return Result.ok(shows);
        } catch (Exception e) {
            log.error("获取演出售票统计失败", e);
            return Result.fail("获取演出数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取订单统计数据
     */
    @GetMapping("/data-screen/orders")
    public Result getOrderStats() {
        try {
            Map<String, Object> orders = dataScreenService.getOrderStats();
            log.info("订单统计数据获取成功");
            return Result.ok(orders);
        } catch (Exception e) {
            log.error("获取订单统计数据失败", e);
            return Result.fail("获取订单数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户限流统计
     */
    @GetMapping("/data-screen/rate-limit")
    public Result getRateLimitStats() {
        try {
            Map<String, Object> rateLimit = dataScreenService.getRateLimitStats();
            log.info("用户限流统计获取成功");
            return Result.ok(rateLimit);
        } catch (Exception e) {
            log.error("获取用户限流统计失败", e);
            return Result.fail("获取限流数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取网站访问统计
     */
    @GetMapping("/data-screen/visits")
    public Result getVisitStats() {
        try {
            Map<String, Object> visits = dataScreenService.getVisitStats();
            log.info("网站访问统计获取成功");
            return Result.ok(visits);
        } catch (Exception e) {
            log.error("获取网站访问统计失败", e);
            return Result.fail("获取访问数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取实时统计数据
     */
    @GetMapping("/data-screen/realtime")
    public Result getRealTimeStats() {
        try {
            Map<String, Object> realtime = dataScreenService.getRealTimeStats();
            log.info("实时统计数据获取成功");
            return Result.ok(realtime);
        } catch (Exception e) {
            log.error("获取实时统计数据失败", e);
            return Result.fail("获取实时数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取完整的数据大屏数据（一次性获取所有）
     */
    @GetMapping("/data-screen/all")
    public Result getAllDataScreenData() {
        try {
            Map<String, Object> allData = new HashMap<>();
            
            // 并行获取所有统计数据
            allData.put("总体概览", dataScreenService.getOverallStats());
            allData.put("营收分析", dataScreenService.getRevenueAnalysis());
            allData.put("演出统计", dataScreenService.getShowTicketStats());
            allData.put("订单统计", dataScreenService.getOrderStats());
            allData.put("限流统计", dataScreenService.getRateLimitStats());
            allData.put("访问统计", dataScreenService.getVisitStats());
            allData.put("实时数据", dataScreenService.getRealTimeStats());
            
            log.info("完整数据大屏数据获取成功");
            return Result.ok(allData);
        } catch (Exception e) {
            log.error("获取完整数据大屏数据失败", e);
            return Result.fail("获取完整数据失败：" + e.getMessage());
        }
    }
}