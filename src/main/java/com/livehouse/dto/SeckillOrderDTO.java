package com.livehouse.dto;

import lombok.Data;

/**
 * 秒杀订单DTO
 */
@Data
public class SeckillOrderDTO {
    
    /**
     * 票种ID
     */
    private Long ticketTypeId;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 用户ID（从登录信息中获取）
     */
    private Long userId;
}