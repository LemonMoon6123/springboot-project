package com.livehouse.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀订单消息DTO
 */
@Data
public class SeckillOrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 演出ID
     */
    private Long showId;
    
    /**
     * 票种ID
     */
    private Long ticketTypeId;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 订单金额
     */
    private BigDecimal amount;
    
    /**
     * 票种名称
     */
    private String ticketTypeName;
    
    /**
     * 单价
     */
    private BigDecimal price;

    /**
     * 幂等请求ID：一次成功的秒杀（Lua脚本执行成功）对应唯一一个requestId，
     * 消费者据此防止同一条消息因框架重试/broker重复投递而被处理多次
     * （避免重复创建订单、重复扣减/归还库存）。
     */
    private String requestId;

    public SeckillOrderMessage() {}

    public SeckillOrderMessage(Long userId, Long showId, Long ticketTypeId, 
                              Integer quantity, BigDecimal amount, 
                              String ticketTypeName, BigDecimal price) {
        this.userId = userId;
        this.showId = showId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.amount = amount;
        this.ticketTypeName = ticketTypeName;
        this.price = price;
    }
}