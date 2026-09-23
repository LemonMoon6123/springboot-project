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
 * 退票记录实体类（本地消息表）
 *
 * 退票申请通过时，在同一个数据库事务里落库这条记录（与订单状态更新原子提交），
 * 再异步发送MQ消息触发库存回补；即使消息发送失败或丢失，这条记录也会一直保持
 * "处理中/失败"状态，可以被定时任务扫描出来重新发送，从而保证库存回补的最终一致性，
 * 不需要依赖MQ本身的可靠性投递机制（本地事务表模式 / Transactional Outbox）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_refund_record")
public class RefundRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0; // 处理中（已受理，库存尚未回补）
    public static final int STATUS_SUCCESS = 1; // 已完成（库存已回补）
    public static final int STATUS_FAILED = 2;  // 处理失败（等待定时任务重试）

    /**
     * 退票记录ID
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
     * 退票数量
     */
    private Integer quantity;

    /**
     * 退款金额
     */
    private BigDecimal amount;

    /**
     * 处理状态：0处理中 1已完成 2处理失败（待重试）
     */
    private Integer status;

    /**
     * 退票原因
     */
    private String reason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;
}
