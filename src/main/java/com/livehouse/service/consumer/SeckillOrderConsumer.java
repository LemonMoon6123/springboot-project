package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.dto.SeckillOrderMessage;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.exception.CustomException;
import com.livehouse.service.IMessageProducerService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.RedisIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀订单消费者
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private IMessageProducerService messageProducerService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消费秒杀订单消息
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    @Transactional
    public void handleSeckillOrder(SeckillOrderMessage message) {
        log.info("收到秒杀订单消息，用户ID：{}，票种ID：{}", 
                message.getUserId(), message.getTicketTypeId());

        try {
            // 1. 生成雪花算法订单号
            Long orderId = redisIDGenerator.getId("ticket_order");

            // 2. 创建订单对象
            TicketOrder order = new TicketOrder();
            order.setId(orderId);
            order.setUserId(message.getUserId());
            order.setShowId(message.getShowId());
            order.setTicketTypeId(message.getTicketTypeId());
            order.setQuantity(message.getQuantity());
            order.setAmount(message.getAmount());
            order.setPayStatus(0); // 未支付
            order.setOrderStatus(1); // 待支付

            // 3. 写入订单表
            ticketOrderService.save(order);

            // 4. 预扣数据库库存（双重保险）
            boolean success = ticketTypeService.update()
                    .setSql("left_stock = left_stock - " + message.getQuantity())
                    .eq("id", message.getTicketTypeId())
                    .gt("left_stock", message.getQuantity() - 1)
                    .update();

            if (!success) {
                // 数据库库存不足，恢复Redis库存
                restoreRedisStock(message);
                throw new CustomException("数据库库存不足，订单创建失败");
            }

            // 5. 发送延迟消息到超时队列
            OrderTimeoutMessage timeoutMessage = new OrderTimeoutMessage(
                orderId, 
                message.getUserId(), 
                message.getTicketTypeId(), 
                message.getQuantity()
            );
            messageProducerService.sendOrderTimeoutMessage(timeoutMessage);

            log.info("订单创建成功，订单ID：{}，用户ID：{}，票种ID：{}，数量：{}", 
                    orderId, message.getUserId(), message.getTicketTypeId(), message.getQuantity());

        } catch (Exception e) {
            log.error("处理秒杀订单消息失败，用户ID：{}，票种ID：{}", 
                    message.getUserId(), message.getTicketTypeId(), e);
            
            // 恢复Redis库存和用户购买记录
            restoreRedisStock(message);
            
            // 清除用户待支付状态
            String userOrderStatusKey = "user:order:status:" + 
                message.getUserId() + ":" + message.getTicketTypeId();
            stringRedisTemplate.delete(userOrderStatusKey);
            
            throw new RuntimeException("订单创建失败", e);
        }
    }

    /**
     * 恢复Redis库存
     */
    private void restoreRedisStock(SeckillOrderMessage message) {
        try {
            // 恢复Redis库存
            String stockKey = "ticket:stock:" + message.getTicketTypeId();
            stringRedisTemplate.opsForValue().increment(stockKey, message.getQuantity());

            // 恢复用户购买记录
            String orderKey = "ticket:order:" + message.getTicketTypeId() + ":" + message.getUserId();
            Long currentCount = stringRedisTemplate.opsForValue().increment(orderKey, -message.getQuantity());
            if (currentCount <= 0) {
                stringRedisTemplate.delete(orderKey);
            }

            log.info("已恢复Redis库存，票种ID：{}，用户ID：{}，数量：{}", 
                    message.getTicketTypeId(), message.getUserId(), message.getQuantity());
        } catch (Exception e) {
            log.error("恢复Redis库存失败，票种ID：{}，用户ID：{}", 
                    message.getTicketTypeId(), message.getUserId(), e);
        }
    }
}