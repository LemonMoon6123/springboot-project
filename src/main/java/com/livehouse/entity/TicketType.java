package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 票种实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_ticket_type")
public class TicketType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 票种ID
     */
    @TableId
    private Long id;

    /**
     * 演出ID
     */
    private Long showId;

    /**
     * 票种名称：早鸟票/预售票/全价票/VIP票
     */
    private String name;

    /**
     * 票价
     */
    private BigDecimal price;

    /**
     * 总库存
     */
    private Integer totalStock;

    /**
     * 剩余库存
     */
    private Integer leftStock;

    /**
     * 开售时间
     */
    private LocalDateTime saleStartTime;

    /**
     * 结束售票时间
     */
    private LocalDateTime saleEndTime;

    /**
     * 每人限购数量
     */
    private Integer limitPerUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}