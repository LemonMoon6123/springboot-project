package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.scaffold.common.FlagDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("user_address")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "收货地址")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    @Schema(description = "地址 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @JsonAlias({"receiverName", "name"})
    @Schema(description = "收货人姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiver;

    @Schema(description = "收货人手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Schema(description = "省份", example = "广东省", requiredMode = Schema.RequiredMode.REQUIRED)
    private String province;

    @Schema(description = "城市", example = "深圳市", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @Schema(description = "区/县", example = "南山区", requiredMode = Schema.RequiredMode.REQUIRED)
    private String district;

    @JsonAlias({"address"})
    @Schema(description = "详细地址", example = "科技园南区1栋", requiredMode = Schema.RequiredMode.REQUIRED)
    private String detail;

    @JsonAlias({"defaultAddress"})
    @JsonDeserialize(using = FlagDeserializer.class)
    @Schema(description = "是否默认地址:1是 0否", example = "1")
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private String createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private String updatedAt;
}
