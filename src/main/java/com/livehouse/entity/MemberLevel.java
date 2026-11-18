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
// 只有当所有字段的值都完全一模一样时，它才会返回 true，并拒绝使用父类的字段值纳入比较范围。
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