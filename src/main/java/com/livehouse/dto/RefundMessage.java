package com.livehouse.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 退票消息DTO
 *
 * 只携带"回补库存"所需的最小信息，退款金额等信息以 {@link com.livehouse.entity.RefundRecord}
 * 落库的记录为准（本地消息表模式：先落库再发消息，消息只是"触发器"，
 * 消费者/定时任务始终以数据库记录的状态为准做幂等判断）。
 */
@Data
public class RefundMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 退票记录ID（tb_refund_record主键）
     */
    private Long refundRecordId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 票种ID
     */
    private Long ticketTypeId;

    /**
     * 退票数量
     */
    private Integer quantity;

    public RefundMessage() {}

    public RefundMessage(Long refundRecordId, Long orderId, Long userId, Long ticketTypeId, Integer quantity) {
        this.refundRecordId = refundRecordId;
        this.orderId = orderId;
        this.userId = userId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
    }
}
