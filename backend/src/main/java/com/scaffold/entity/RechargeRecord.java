package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("recharge_record")
@Schema(description = "充值记录")
public class RechargeRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "充值金额", example = "100.0")
    private Double amount;

    @Schema(description = "充值后余额", example = "1100.0")
    private Double balanceAfter;

    @Schema(description = "备注", example = "用户充值")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private String createdAt;
}
