package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.entity.TicketOrder;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.TicketOrderStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单超时死信队列消费者
 *
 * 回补顺序：先 MySQL，再 Redis。
 * 若先还 Redis、MySQL 失败，会造成 Redis 虚高，后续出现「Lua 通过但建单扣库存失败」。
 * 业务流程：只会针对未支付订单消息。先设置订单状态为已取消，再归还数据库库存，最后回滚redis缓存库存 + 用户限购次数、清除用户待支付状态。
 * 兜底：本方法带 @Transactional，CAS取消订单与MySQL归还库存在同一事务内原子提交/回滚，
 * 因此不会出现「订单已取消但DB库存没还」的半完成态——失败时订单会回滚为待支付。
 * 本死信队列未再绑定下级死信交换机，重试(max-attempts)耗尽后 nack(requeue=false) 消息会被broker直接丢弃；
 * 丢弃时残留「订单待支付 + Redis库存少一份」，由 OrderTimeoutScheduledTask 每5分钟扫描待支付超时订单二次兜底收敛。
 * 若DB归还成功但Redis归还失败，则将Redis校准对齐DB当前值。
 */
@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private TicketOrderStockService ticketOrderStockService;

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

            if (order.getExpireTime() != null && order.getExpireTime().isAfter(LocalDateTime.now())) {
                log.info("未到支付截止时间 expire_time={}，跳过过早超时消息，订单ID：{}",
                        order.getExpireTime(), message.getOrderId());
                return;
            }

            // CAS 取消：与定时任务互斥，避免两边各归还一次库存
            boolean cancelled = ticketOrderService.update()
                    .set("order_status", 3)
                    .set("cancel_time", LocalDateTime.now())
                    .set("cancel_reason", "超时未支付")
                    .eq("id", message.getOrderId())
                    .eq("order_status", 1)
                    .eq("pay_status", 0)
                    .update();
            if (!cancelled) {
                log.info("订单状态已变更，跳过超时取消，订单ID：{}", message.getOrderId());
                return;
            }

            ticketOrderStockService.releaseReservedStock(order);

            // 清除用户对该票的待支付状态，一直没支付订单都超时了就清除该状态。
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
