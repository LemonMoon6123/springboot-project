package com.livehouse.service.impl;

import com.livehouse.dto.Result;
import com.livehouse.dto.SeckillOrderMessage;
import com.livehouse.dto.UserDTO;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.exception.CustomException;
import com.livehouse.service.IMessageProducerService;
import com.livehouse.service.ISeckillTicketService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.RedisIDGenerator;
import com.livehouse.utils.SeckillScriptExecutor;
import com.livehouse.utils.SimpleRedisLock;
import com.livehouse.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillTicketServiceImpl implements ISeckillTicketService {

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IMessageProducerService messageProducerService;

    /**
     *
     * @param ticketTypeId 票种ID
     * @param quantity 购买数量
     *
     * 该方法运用业务状态幂等控制策略，解决同一用户多次请求时的重复下单问题（根源在于票的limit限制不是1，可以买多张票，造成多买）。
     * 并结合Redisson分布式锁挡住大量无效请求线程，快速失败返回结果。
     */
    public Result seckillTicket(Long ticketTypeId, Integer quantity) {
        // 1.先做基础购买资格检验。
//        // 1.1 查询票种信息
//        TicketType ticketType = ticketTypeService.getById(ticketTypeId);
//        if (ticketType == null) {
//            return Result.fail("票种不存在");
//        }
//
//        // 1.2 检查售票时间
//        LocalDateTime now = LocalDateTime.now();
//        if (ticketType.getSaleStartTime().isAfter(now)) {
//            return Result.fail("售票尚未开始！");
//        }
//        if (ticketType.getSaleEndTime() != null && ticketType.getSaleEndTime().isBefore(now)) {
//            return Result.fail("售票已经结束啦！");
//        }
//
//        // 1.3 校验购买数量
//        if (quantity == null || quantity == 0) {
//            return Result.fail("购买数量不能为空！");
//        }

        // 1.4 获取当前用户信息
        Long userId = UserHolder.getUser().getId();

        // 1.5 业务状态幂等检查
        String userOrderStatusKey = "user:order:status:" + userId + ":" + ticketTypeId;
        String orderStatus = stringRedisTemplate.opsForValue().get(userOrderStatusKey);
        if ("PENDING".equals(orderStatus)) {
            return Result.fail("您有未支付的订单，请先完成支付！");
        }

        // 2.为线程创建分布式锁
        String lockKey = "seckill:lock:" + ticketTypeId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 2.1 尝试获取分布式锁
            boolean getLock = lock.tryLock();
            // 2.2 如果获取锁失败，代表是相同用户线程，则挡住多余请求线程。
            if (!getLock) {
                return Result.fail("请勿重复点击！");
            }

            // 2.3 再次检查Redis状态，更稳妥一些。
            orderStatus = stringRedisTemplate.opsForValue().get(userOrderStatusKey);
            if ("PENDING".equals(orderStatus)) {
                return Result.fail("您有未支付的订单，请先完成支付！");
            }

            // 2.4 如果获取锁成功，执行Lua脚本，进行购买资格校验和扣减redis库存等操作。
            // 查询票种信息
            TicketType ticketType = ticketTypeService.getById(ticketTypeId);
            SeckillScriptExecutor.SeckillResult seckillResult = seckillScriptExecutor.executeSeckill(
                    ticketTypeId, userId, quantity, ticketType.getLimitPerUser());
            // 2.5 根据脚本执行结果处理
            if (!seckillResult.isSuccess()) {
                return Result.fail(seckillResult.getMessage());
            }

            // 2.6 标记用户有待支付订单（设置30分钟过期）
            stringRedisTemplate.opsForValue().set(userOrderStatusKey, "PENDING", Duration.ofMinutes(30));

            // 2.7 Lua脚本成功，发送消息到队列异步创建订单
            try {
                SeckillOrderMessage orderMessage = new SeckillOrderMessage(
                    userId, 
                    ticketType.getShowId(),
                    ticketTypeId, 
                    quantity, 
                    ticketType.getPrice().multiply(new BigDecimal(quantity)),
                    ticketType.getName(),
                    ticketType.getPrice()
                );
                
                messageProducerService.sendSeckillOrderMessage(orderMessage);
                
                log.info("秒杀成功，已发送订单消息，用户ID：{}，票种ID：{}", userId, ticketTypeId);
                return Result.ok("抢票成功！正在生成订单，请稍后查看...");
                
            } catch (Exception e) {
                log.error("发送订单消息失败，恢复Redis库存，用户ID：{}，票种ID：{}", userId, ticketTypeId, e);
                // 消息发送失败，需要恢复Redis库存
                restoreStock(ticketTypeId, userId, quantity);
                return Result.fail("系统繁忙，请稍后再试！");
            }
        }catch (Exception e) {
            return Result.fail("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {  // 检查是否持有锁
                // 2.9 释放锁
                lock.unlock();
            }
        }
    }

    /**
     * 创建票务订单
     */
    @Transactional
    public Long createTicketOrder(Long ticketTypeId, Long userId, Integer quantity, TicketType ticketType) {
        // 1. 生成订单ID
        Long orderId = redisIDGenerator.getId("ticket_order");

        // 2. 创建订单
        TicketOrder order = new TicketOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setShowId(ticketType.getShowId());
        order.setTicketTypeId(ticketTypeId);
        order.setQuantity(quantity);
        order.setAmount(ticketType.getPrice().multiply(new BigDecimal(quantity)));

        // 3. 保存订单
        ticketOrderService.save(order);

        // 4. 扣减数据库库存（双重保险）
        boolean success = ticketTypeService.update()
                .setSql("left_stock = left_stock - " + quantity)
                .eq("id", ticketTypeId)
                .gt("left_stock", quantity - 1)
                .update();

        if (!success) {
            throw new CustomException("库存不足，订单创建失败");
        }

        log.info("成功创建订单，订单ID：{}，票种ID：{}，用户ID：{}，数量：{}", orderId, ticketTypeId, userId, quantity);
        return orderId;
    }

    /**
     * 恢复库存（当订单创建失败时）
     */
    private void restoreStock(Long ticketTypeId, Long userId, Integer quantity) {
        try {
            // 恢复Redis库存
            String stockKey = "ticket:stock:" + ticketTypeId;
            stringRedisTemplate.opsForValue().increment(stockKey, quantity);

            // 恢复用户购买记录
            String orderKey = "ticket:order:" + ticketTypeId + ":" + userId;
            Long currentCount = stringRedisTemplate.opsForValue().increment(orderKey, -quantity);
            if (currentCount <= 0) {
                stringRedisTemplate.delete(orderKey);
            }
            log.info("已恢复库存，票种ID：{}，用户ID：{}，数量：{}", ticketTypeId, userId, quantity);
        } catch (Exception e) {
            log.error("恢复库存失败，票种ID：{}，用户ID：{}", ticketTypeId, userId, e);
        }
    }
}