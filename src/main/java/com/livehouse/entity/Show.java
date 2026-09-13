package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
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
     * 演出结束时间
     */
    private LocalDateTime endTime;

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