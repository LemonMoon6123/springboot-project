package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "用户名", example = "user", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度为 3-32 位")
    private String username;

    @Schema(description = "密码(明文,服务端 bcrypt 加盐)", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码至少 6 位")
    private String password;

    @Schema(description = "昵称", example = "张三")
    @Size(max = 64, message = "昵称过长")
    private String nickname;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 20, message = "手机号过长")
    private String phone;

    /** USER | MERCHANT，默认 USER */
    @Schema(description = "注册角色:USER(普通用户,默认) | MERCHANT(商家)", example = "USER")
    private String role;

    @Schema(description = "店铺名称(商家注册时填写)", example = "张三数码旗舰店")
    @Size(max = 100, message = "店铺名称过长")
    private String shopName;

    @Schema(description = "店铺简介(商家注册时填写)", example = "主营数码电子产品")
    @Size(max = 500, message = "店铺简介过长")
    private String shopDesc;

    /** 营业执照图片 URL */
    @Schema(description = "营业执照图片 URL(商家注册时填写)")
    @Size(max = 512, message = "营业执照地址过长")
    private String licenseImage;

    /** 身份证正面 URL */
    @Schema(description = "身份证正面图片 URL(商家注册时填写)")
    @Size(max = 512, message = "身份证正面地址过长")
    private String idCardFront;

    /** 身份证反面 URL */
    @Schema(description = "身份证反面图片 URL(商家注册时填写)")
    @Size(max = 512, message = "身份证反面地址过长")
    private String idCardBack;
}
