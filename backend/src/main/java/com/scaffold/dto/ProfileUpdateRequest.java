package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改个人资料请求")
public class ProfileUpdateRequest {

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像 URL", example = "/uploads/avatar.jpg")
    private String avatar;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "店铺名称(商家)", example = "张三数码旗舰店")
    private String shopName;

    @Schema(description = "店铺简介(商家)", example = "主营数码电子产品")
    private String shopDesc;
}
