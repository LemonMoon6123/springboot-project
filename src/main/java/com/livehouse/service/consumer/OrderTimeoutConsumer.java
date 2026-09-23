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
 *
 * 回补顺序：先 MySQL，再 Redis。
 * 若先还 Redis、MySQL 失败，会造成 Redis 虚高，后续出现「Lua 通过但建单扣库存失败」。
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

    @RabbitListener(queues = RabbitMQConfig.DLX_QUEUE)
    @Transactional
    public void handleOrderTimeout(OrderTimeoutMessage message) {
        log.info("收到订单超时消息，订单ID：{}", message.getOrderId());

        try {
            TicketOrder order = ticketOrderService.getById(message.getOrderId());

            if (order == null) {
                log.warn("订单不存在，订单ID：{}", message.getOrderId());
                return;
            }

            if (order.getPayStatus() == 1) {
                log.info("订单已支付，无需取消，订单ID：{}", message.getOrderId());
                return;
            }

            if (order.getOrderStatus() == 3) {
                log.info("订单已取消，订单ID：{}", message.getOrderId());
                return;
            }

            // CAS 取消：与定时任务互斥，避免两边各归还一次库存
            boolean cancelled = ticketOrderService.update()
                    .set("orderStatus", 3)
                    .eq("id", message.getOrderId())
                    .eq("orderStatus", 1)
                    .eq("payStatus", 0)
                    .update();
            if (!cancelled) {
                log.info("订单状态已变更，跳过超时取消，订单ID：{}", message.getOrderId());
                return;
            }

            // 1. 先归还数据库库存
            boolean dbRestoreSuccess = ticketTypeService.update()
                    .setSql("left_stock = left_stock + " + message.getQuantity())
                    .eq("id", message.getTicketTypeId())
                    .update();

            if (!dbRestoreSuccess) {
                // MySQL 没还成功就不要还 Redis，否则必然虚高；抛出触发重试
                throw new IllegalStateException("数据库库存归还失败，票种ID：" + message.getTicketTypeId());
            }

            // 2. 再归还 Redis 库存 + 用户限购计数
            Long result = seckillScriptExecutor.executeRestoreStock(
                    message.getTicketTypeId(),
                    message.getUserId(),
                    message.getQuantity()
            );
            if (result == null || result != 0L) {
                log.error("Redis库存归还失败，订单ID：{}，结果码：{}", message.getOrderId(), result);
                // MySQL 已还：将 Redis 校准到当前 DB，避免只还了一边
                Integer dbLeft = ticketTypeService.getById(message.getTicketTypeId()).getLeftStock();
                seckillScriptExecutor.syncRedisStockFromDb(message.getTicketTypeId(), dbLeft);
            }

            String userOrderStatusKey = "user:order:status:" +
                    message.getUserId() + ":" + message.getTicketTypeId();
            stringRedisTemplate.delete(userOrderStatusKey);

            log.info("订单超时取消成功，订单ID：{}，票种ID：{}，数量：{}",
                    message.getOrderId(), message.getTicketTypeId(), message.getQuantity());

        } catch (Exception e) {
            log.error("处理订单超时消息失败，订单ID：{}", message.getOrderId(), e);
            throw e;
        }
    }
}
