package com.livehouse.service;

import com.livehouse.dto.RefundMessage;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.RefundRecord;
import com.livehouse.entity.TicketOrder;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.service.ITicketOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 退款审核通过后的订单作废与异步库存回补（与 {@link com.livehouse.service.consumer.RefundConsumer} 配合）。
 */
@Slf4j
@Service
public class RefundFulfillmentService {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ElectronicTicketMapper electronicTicketMapper;

    @Autowired
    private IMessageProducerService messageProducerService;

    @Autowired
    private AppMessageService appMessageService;

    @Transactional
    public void onAuditApproved(RefundRecord record) {
        if (record == null || record.getId() == null) {
            throw new IllegalArgumentException("退票记录无效");
        }
        TicketOrder order = ticketOrderService.getById(record.getOrderId());
        if (order == null) {
            throw new IllegalStateException("关联订单不存在");
        }
        if (order.getOrderStatus() != null && order.getOrderStatus() == 4) {
            log.info("订单已为已退票，跳过重复处理，orderId={}", order.getId());
            dispatchStockRestoreMessage(record);
            return;
        }
        if (order.getPayStatus() == null || order.getPayStatus() != 1) {
            throw new IllegalStateException("订单未支付，无法完成退票");
        }

        // 1.更改订单状态
        order.setOrderStatus(4);
        order.setPayStatus(2);
        order.setUpdateTime(LocalDateTime.now());
        ticketOrderService.updateById(order);

        // 2.更改电子票状态
        electronicTicketMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, order.getId())
                .eq(ElectronicTicket::getVerifyStatus, 0)
                .set(ElectronicTicket::getVerifyStatus, 2));

        // 3.封装退票消息，送给生产者
        dispatchStockRestoreMessage(record);

        // 4.插入消息通知
        appMessageService.insertMessage(record.getUserId(), "order", "退票审核通过",
                "您的退款申请已通过，款项将原路退回。", "refund", String.valueOf(order.getId()));
        log.info("退票审核通过，已更新订单并发送库存回补消息，refundId={}，orderId={}", record.getId(), order.getId());
    }

    private void dispatchStockRestoreMessage(RefundRecord record) {
        RefundMessage message = new RefundMessage(
                record.getId(), record.getOrderId(), record.getUserId(),
                record.getTicketTypeId(), record.getQuantity());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messageProducerService.sendRefundMessage(message);
                }
            });
        } else {
            messageProducerService.sendRefundMessage(message);
        }
    }
}