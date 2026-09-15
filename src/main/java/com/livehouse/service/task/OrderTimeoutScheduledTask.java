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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时定时任务（兜底机制）
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

    /**
     * 每5分钟执行一次，扫描超时未支付订单
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 5分钟
    @Transactional
    public void cancelTimeoutOrders() {
        log.info("开始执行订单超时兜底任务");

        try {
            // 1. 查询15分钟前创建且未支付的订单
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(15);
            
            List<TicketOrder> timeoutOrders = ticketOrderService.lambdaQuery()
                    .eq(TicketOrder::getPayStatus, 0) // 未支付
                    .eq(TicketOrder::getOrderStatus, 1) // 待支付
                    .lt(TicketOrder::getCreateTime, timeoutThreshold)
                    .list();

            if (timeoutOrders.isEmpty()) {
                log.info("没有超时订单需要处理");
                return;
            }

            log.info("发现{}个超时订单需要取消", timeoutOrders.size());

            // 2. 批量取消订单
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

    /**
     * 取消单个订单
     */
    private void cancelOrder(TicketOrder order) {
        log.info("开始取消超时订单，订单ID：{}", order.getId());

        // 1. 更新订单状态为已取消
        order.setOrderStatus(3); // 已取消
        ticketOrderService.updateById(order);

        // 2. 归还Redis库存
        Long result = seckillScriptExecutor.executeRestoreStock(
                order.getTicketTypeId(),
                order.getUserId(),
                order.getQuantity()
        );

        if (result == 0) {
            log.info("Redis库存归还成功，订单ID：{}，票种ID：{}，数量：{}",
                    order.getId(), order.getTicketTypeId(), order.getQuantity());
        } else {
            log.error("Redis库存归还失败，订单ID：{}，结果码：{}", order.getId(), result);
        }

        // 3. 归还数据库库存
        boolean dbRestoreSuccess = ticketTypeService.update()
                .setSql("left_stock = left_stock + " + order.getQuantity())
                .eq("id", order.getTicketTypeId())
                .update();

        if (dbRestoreSuccess) {
            log.info("数据库库存归还成功，订单ID：{}，票种ID：{}，数量：{}",
                    order.getId(), order.getTicketTypeId(), order.getQuantity());
        } else {
            log.warn("数据库库存归还失败，订单ID：{}，票种ID：{}，数量：{}",
                    order.getId(), order.getTicketTypeId(), order.getQuantity());
        }

        // 4. 清除用户待支付状态
        String userOrderStatusKey = "user:order:status:" +
                order.getUserId() + ":" + order.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);

        log.info("订单取消完成，订单ID：{}", order.getId());
    }
}