package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 会员等级配置
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_member_level")
public class MemberLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "level", type = IdType.INPUT)
    private Integer level;

    private String name;

    private Integer minTotalCredits;

    private BigDecimal discountRate;

    private Integer priorityMinutes;

    private String benefitsJson;

    private String cardBg;

    private Integer status;
}