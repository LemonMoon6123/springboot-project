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
 * 演出项目（巡演聚合根）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_performance")
public class Performance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long artistId;

    private String artistName;

    private String type;

    private String city;

    private String poster;

    private String posterTag;

    private String description;

    /** 是否实名制：0否 1是 */
    private Integer isRealName;

    private BigDecimal minPrice;

    private Integer sessionCount;

    private Integer wantCount;

    /** 1草稿 2已上架 3已下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}