package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "账号资料")
public class AccountProfile {

    @Schema(description = "账号 ID")
    private Long id;

    @Schema(description = "用户名", example = "user")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像 URL", example = "/uploads/avatar.jpg")
    private String avatar;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    /** ADMIN / USER / MERCHANT */
    @Schema(description = "角色:ADMIN/USER/MERCHANT", example = "USER")
    private String role;

    /** ADMIN | USER | MERCHANT */
    @Schema(description = "账号类型:ADMIN(管理员) | USER(用户) | MERCHANT(商家)", example = "USER")
    private String accountType;

    @Schema(description = "账号状态:1正常 0禁用", example = "1")
    private Integer status;

    @Schema(description = "账户余额", example = "1000.0")
    private Double balance;

    @Schema(description = "商家审核状态:0待审核 1通过 2拒绝", example = "1")
    private Integer auditStatus;

    @Schema(description = "店铺名称(商家)", example = "张三数码旗舰店")
    private String shopName;

    @Schema(description = "店铺简介(商家)", example = "主营数码电子产品")
    private String shopDesc;

    @Schema(description = "审核备注(商家)", example = "审核通过")
    private String auditRemark;

    @Schema(description = "创建时间")
    private String createdAt;
}
