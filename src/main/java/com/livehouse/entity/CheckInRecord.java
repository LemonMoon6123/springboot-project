package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 入场核销记录实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_check_in_record")
public class CheckInRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 核销记录ID
     */
    @TableId
    private Long id;

    /**
     * 电子票号
     */
    private Long ticketId;

    /**
     * 演出ID
     */
    private Long showId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 操作员ID
     */
    private Long operatorId;

    /**
     * 核销时间
     */
    private LocalDateTime checkTime;
}