package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "用户名", example = "user")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "角色", example = "USER")
    private String role;

    @Schema(description = "头像 URL", example = "/uploads/avatar.jpg")
    private String avatar;

    @Schema(description = "用户 ID", example = "1")
    private Long userId;

    /** ADMIN | USER | MERCHANT */
    @Schema(description = "账号类型:ADMIN(管理员) | USER(用户) | MERCHANT(商家)", example = "USER")
    private String accountType;

    @Schema(description = "欢迎消息", example = "登录成功")
    private String message;
}
