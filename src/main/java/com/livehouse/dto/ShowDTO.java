package com.livehouse.dto;

import com.livehouse.entity.TicketType;
import com.livehouse.entity.Venue;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演出详情DTO（包含场馆信息和票种信息）
 */
@Data
public class ShowDTO {
    /**
     * 演出ID
     */
    private Long id;

    /**
     * 场馆信息
     */
    private Venue venue;

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
     * 票种列表
     */
    private List<TicketType> ticketTypes;

    /**
     * 距离（用于地理位置查询）
     */
    private Double distance;
}