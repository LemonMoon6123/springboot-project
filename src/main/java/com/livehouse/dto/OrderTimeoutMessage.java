package com.livehouse.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单超时消息DTO
 */
@Data
public class OrderTimeoutMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
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
     * 购买数量
     */
    private Integer quantity;

    public OrderTimeoutMessage() {}

    public OrderTimeoutMessage(Long orderId, Long userId, Long ticketTypeId, Integer quantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
    }
}