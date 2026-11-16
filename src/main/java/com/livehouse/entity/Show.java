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
 * 演出实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_show")
public class Show implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 演出ID
     */
    @TableId
    private Long id;

    /**
     * 所属演出项目ID
     */
    private Long performanceId;

    /**
     * 场馆ID
     */
    private Long venueId;

    /**
     * 艺人/乐队
     */
    private String artist;

    /**
     * 演出标题
     */
    private String title;

    /**
     * 演出类型
     */
    private String type;

    /**
     * 演出开始时间
     */
    private LocalDateTime startTime;

    /**
     * 场次标签，如「周六 20:00 / 加场」
     */
    private String sessionLabel;

    /**
     * 演出结束时间
     */
    private LocalDateTime endTime;

    /**
     * 是否实名制：0否 1是
     */
    private Integer isRealName;

    /**
     * 该场次最低票价（冗余）
     */
    private BigDecimal minPrice;

    /**
     * 已售张数（冗余）
     */
    private Integer sellCount;

    /**
     * 入场口配置，如「A,B」
     */
    private String gateConfig;

    /**
     * 状态：1待开票 2售票中 3售罄 4已结束
     */
    private Integer status;

    /**
     * 演出介绍
     */
    private String description;

    /**
     * 演出海报
     */
    private String image;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}