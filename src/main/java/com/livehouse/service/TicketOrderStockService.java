package com.livehouse.service;

import com.livehouse.entity.StatusCode;
import com.livehouse.entity.TicketOrder;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.utils.RedisConstants;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 订单与票种 {@code left_stock} / Redis 库存的一致性：建单预占、支付落库、取消/超时释放。
 */
@Slf4j
@Service
public class TicketOrderStockService {

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 建单后写入：Redis 预占时 dbStockDeducted=0，直接扣 DB 时为 1 */
    public static int dbStockFlag(boolean deductedInDbAtCreate) {
        return deductedInDbAtCreate ? 1 : 0;
    }

    /**
     * 支付成功时扣减 MySQL 库存（Redis 预占的订单在此落库；秒杀建单已扣过则跳过）。
     */
    public void confirmDbStockOnPayment(TicketOrder order) {
        if (order == null || order.getTicketTypeId() == null || order.getQuantity() == null) {
            return;
        }
        if (!shouldDeductDbOnPay(order)) {
            return;
        }
        boolean ok = ticketTypeService.update()
                .setSql("left_stock = left_stock - " + order.getQuantity())
                .eq("id", order.getTicketTypeId())
                .ge("left_stock", order.getQuantity())
                .update();
        if (!ok) {
            log.error("支付成功但数据库库存不足，orderId={}，ticketTypeId={}，qty={}",
                    order.getId(), order.getTicketTypeId(), order.getQuantity());
            throw new StatusCodeException(StatusCode.STOCK_NOT_ENOUGH, "支付时库存不足");
        }
        order.setDbStockDeducted(1);
        log.info("支付成功已扣减数据库库存，orderId={}，ticketTypeId={}，qty={}",
                order.getId(), order.getTicketTypeId(), order.getQuantity());
    }

    /** 取消/超时：按标记归还 DB；有 Redis 库存键时归还 Redis 限购计数 */
    public void releaseReservedStock(TicketOrder order) {
        if (order == null || order.getTicketTypeId() == null || order.getQuantity() == null) {
            return;
        }
        if (shouldRestoreDbOnCancel(order)) {
            ticketTypeService.update()
                    .setSql("left_stock = left_stock + " + order.getQuantity())
                    .eq("id", order.getTicketTypeId())
                    .update();
        }
        String stockKey = RedisConstants.TICKET_STOCK_KEY + order.getTicketTypeId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            Long result = seckillScriptExecutor.executeRestoreStock(
                    order.getTicketTypeId(), order.getUserId(), order.getQuantity());
            if (result == null || result != 0L) {
                Integer dbLeft = ticketTypeService.getById(order.getTicketTypeId()).getLeftStock();
                seckillScriptExecutor.syncRedisStockFromDb(order.getTicketTypeId(), dbLeft);
            }
        }
    }

    private static boolean shouldDeductDbOnPay(TicketOrder order) {
        if (order.getDbStockDeducted() != null && order.getDbStockDeducted() == 1) {
            return false;
        }
        if ("seckill".equals(order.getChannel())) {
            return false;
        }
        return true;
    }

    private static boolean shouldRestoreDbOnCancel(TicketOrder order) {
        if (order.getDbStockDeducted() != null) {
            return order.getDbStockDeducted() == 1;
        }
        return "seckill".equals(order.getChannel());
    }
}