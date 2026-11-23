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
 * 票务订单实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_ticket_order")
public class TicketOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单号（雪花ID）
     */
    @TableId
    private Long id;

    /**
     * 业务订单号（展示用）
     */
    private String orderNo;

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
     * 购票数量
     */
    private Integer quantity;

    /**
     * 订单金额（应付总额，语义与历史保持一致）
     */
    private BigDecimal amount;

    /**
     * 票面总额 = 单价 × 数量
     */
    private BigDecimal totalAmount;

    /**
     * 服务费
     */
    private BigDecimal serviceFee;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 实付金额
     */
    private BigDecimal payAmount;

    /**
     * 预留历史兼容，v2 恒为 NULL
     */
    private Long userCouponId;

    /**
     * 下单时会员等级快照（0/1/2/3）
     */
    private Integer memberLevel;

    /**
     * 会员等级优惠金额
     */
    private BigDecimal memberDiscountAmount;

    /**
     * 支付截止时间
     */
    private LocalDateTime expireTime;

    /**
     * 支付状态：0未支付 1已支付 2已取消
     */
    private Integer payStatus;

    /**
     * 订单状态：1待支付 2已完成 3已取消 4已退票
     */
    private Integer orderStatus;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 幂等请求ID：来源于秒杀成功后发出的MQ消息，用于防止消息重复投递/消费者内部重试
     * 导致同一次抢购被重复创建订单。数据库层面加唯一索引作为兜底保障。
     */
    private String requestId;

    /**
     * 客户端幂等键（App 下单防重复提交）
     */
    private String idempotentKey;

    /**
     * 场次ID（= tb_show.id）
     */
    private Long sessionId;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 下单渠道：mp/h5/app
     */
    private String channel;

    /**
     * MySQL left_stock 是否已扣减：0 否（仅 Redis 预占，支付后扣库），1 是（建单或支付已扣）
     */
    private Integer dbStockDeducted;

    /**
     * 入场子状态：0待入场 1已核销
     */
    private Integer entryStatus;

    /**
     * 列表软删除：0正常 1已删除
     */
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}