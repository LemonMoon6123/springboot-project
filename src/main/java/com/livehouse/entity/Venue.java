package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 演出场馆实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_venue")
public class Venue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 场馆ID
     */
    @TableId
    private Long id;

    /**
     * 场馆名称
     */
    private String name;

    /**
     * 场馆地址
     */
    private String address;

    /**
     * 所在城市
     */
    private String city;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 总容量
     */
    private Integer capacity;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}