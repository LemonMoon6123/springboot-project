package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.entity.TicketOrder;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单超时死信队列消费者
 */
@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消费订单超时消息
     */
    @RabbitListener(queues = RabbitMQConfig.DLX_QUEUE)
    @Transactional
    public void handleOrderTimeout(OrderTimeoutMessage message) {
        log.info("收到订单超时消息，订单ID：{}", message.getOrderId());

        try {
            // 1. 查询订单状态
            TicketOrder order = ticketOrderService.getById(message.getOrderId());
            
            if (order == null) {
                log.warn("订单不存在，订单ID：{}", message.getOrderId());
                return;
            }

            // 2. 检查订单是否已支付
            if (order.getPayStatus() == 1) {
                log.info("订单已支付，无需取消，订单ID：{}", message.getOrderId());
                return;
            }

            // 3. 检查订单是否已取消
            if (order.getOrderStatus() == 3) {
                log.info("订单已取消，订单ID：{}", message.getOrderId());
                return;
            }

            // 4. 取消订单
            order.setOrderStatus(3); // 已取消
            ticketOrderService.updateById(order);

            // 5. 调用Lua脚本原子归还Redis库存
            Long result = seckillScriptExecutor.executeRestoreStock(
                message.getTicketTypeId(), 
                message.getUserId(), 
                message.getQuantity()
            );

            if (result == 0) {
                log.info("订单超时取消成功，已归还库存，订单ID：{}，票种ID：{}，数量：{}", 
                        message.getOrderId(), message.getTicketTypeId(), message.getQuantity());
            } else {
                log.error("归还库存失败，订单ID：{}，结果码：{}", message.getOrderId(), result);
            }

            // 6. 清除用户待支付状态
            String userOrderStatusKey = "user:order:status:" + 
                message.getUserId() + ":" + message.getTicketTypeId();
            stringRedisTemplate.delete(userOrderStatusKey);

            // 7. 归还数据库库存（票种表）
            boolean dbRestoreSuccess = ticketTypeService.update()
                    .setSql("left_stock = left_stock + " + message.getQuantity())
                    .eq("id", message.getTicketTypeId())
                    .update();

            if (dbRestoreSuccess) {
                log.info("数据库库存归还成功，票种ID：{}，数量：{}", 
                        message.getTicketTypeId(), message.getQuantity());
            } else {
                log.warn("数据库库存归还失败，票种ID：{}，数量：{}", 
                        message.getTicketTypeId(), message.getQuantity());
            }

        } catch (Exception e) {
            log.error("处理订单超时消息失败，订单ID：{}", message.getOrderId(), e);
        }
    }
}