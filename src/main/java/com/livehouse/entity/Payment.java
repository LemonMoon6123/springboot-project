package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_CLOSED = 3;
    public static final int STATUS_REFUNDED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private String channel;

    private String tradeNo;

    private String prepayId;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime notifyTime;

    private String rawNotify;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}