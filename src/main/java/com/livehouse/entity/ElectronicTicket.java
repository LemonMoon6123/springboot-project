package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 电子票实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_electronic_ticket")
public class ElectronicTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 电子票号
     */
    @TableId
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

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
     * 核销码
     */
    private String verifyCode;

    /**
     * 核销状态：0未核销 1已核销 2已作废（退票）
     */
    private Integer verifyStatus;

    /**
     * 核销时间
     */
    private LocalDateTime verifyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}