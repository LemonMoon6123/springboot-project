package com.livehouse.service.task;

import com.livehouse.entity.TicketOrder;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时定时任务（兜底机制）
 * 这个兜底定时任务是防止超时订单消费者重试次数耗尽归还库存失败所造成的“零元购”现象，因为订单超时死信队列没有绑死信交换机和死信队列。
 */
@Slf4j
@Component
public class OrderTimeoutScheduledTask {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cancelTimeoutOrders() {
        log.info("开始执行订单超时兜底任务");

        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(15);

            List<TicketOrder> timeoutOrders = ticketOrderService.lambdaQuery()
                    .eq(TicketOrder::getPayStatus, 0)
                    .eq(TicketOrder::getOrderStatus, 1)
                    .lt(TicketOrder::getCreateTime, timeoutThreshold)
                    .list();

            if (timeoutOrders.isEmpty()) {
                log.info("没有超时订单需要处理");
                return;
            }

            log.info("发现{}个超时订单需要取消", timeoutOrders.size());

            for (TicketOrder order : timeoutOrders) {
                try {
                    cancelOrder(order);
                } catch (Exception e) {
                    log.error("取消订单失败，订单ID：{}", order.getId(), e);
                }
            }

            log.info("订单超时兜底任务执行完成，处理了{}个订单", timeoutOrders.size());

        } catch (Exception e) {
            log.error("订单超时兜底任务执行失败", e);
        }
    }

    private void cancelOrder(TicketOrder order) {
        log.info("开始取消超时订单，订单ID：{}", order.getId());

        boolean cancelled = ticketOrderService.update()
                .set("orderStatus", 3)
                .eq("id", order.getId())
                .eq("orderStatus", 1)
                .eq("payStatus", 0)
                .update();
        if (!cancelled) {
            log.info("订单已被其它路径取消或支付，跳过，订单ID：{}", order.getId());
            return;
        }

        // 恢复逻辑与订单超时恢复逻辑基本一致，只是新增逻辑字段辅助
        boolean dbRestored = false;
        try {
            boolean dbRestoreSuccess = ticketTypeService.update()
                    .setSql("left_stock = left_stock + " + order.getQuantity())
                    .eq("id", order.getTicketTypeId())
                    .update();

            if (!dbRestoreSuccess) {
                throw new IllegalStateException("数据库库存归还失败，订单ID：" + order.getId());
            }
            dbRestored = true;

            Long result = seckillScriptExecutor.executeRestoreStock(
                    order.getTicketTypeId(),
                    order.getUserId(),
                    order.getQuantity()
            );

            if (result == null || result != 0L) {
                log.error("Redis库存归还失败，订单ID：{}，结果码：{}", order.getId(), result);
                Integer dbLeft = ticketTypeService.getById(order.getTicketTypeId()).getLeftStock();
                seckillScriptExecutor.syncRedisStockFromDb(order.getTicketTypeId(), dbLeft);
            }

            String userOrderStatusKey = "user:order:status:" +
                    order.getUserId() + ":" + order.getTicketTypeId();
            stringRedisTemplate.delete(userOrderStatusKey);
        } catch (Exception e) {
            if (!dbRestored) { // DB归还失败就回滚订单状态
                ticketOrderService.update()
                        .set("orderStatus", 1)
                        .eq("id", order.getId())
                        .eq("orderStatus", 3)
                        .eq("payStatus", 0)
                        .update();
            }
            throw e;
        }

        log.info("订单取消完成，订单ID：{}", order.getId());
    }
}
