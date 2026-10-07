package com.scaffold.controller;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.dto.AccountProfile;
import com.scaffold.dto.LoginRequest;
import com.scaffold.dto.LoginResponse;
import com.scaffold.dto.PasswordChangeRequest;
import com.scaffold.dto.ProfileUpdateRequest;
import com.scaffold.dto.RegisterRequest;
import com.scaffold.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Tag(name = "A01-公共-认证", description = "登录、注册、获取当前用户、修改资料、修改密码")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/auth/login")
    @Operation(summary = "账号登录", description = "用户名+密码登录,返回 JWT token 与账号信息")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/auth/register")
    @Operation(summary = "账号注册", description = "支持注册普通用户或商家(需上传营业执照/身份证),返回 JWT token")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @GetMapping("/auth/me")
    @Operation(summary = "获取当前登录用户", description = "依据 JWT 解析当前账号资料(用户/商家/管理员)")
    public Result<AccountProfile> me(@Parameter(hidden = true) HttpServletRequest request) {
        Long accountId = AuthContext.getUserId(request);
        String accountType = AuthContext.getAccountType(request);
        return Result.ok(authService.getCurrentUser(accountId, accountType));
    }

    @PutMapping("/auth/profile")
    @Operation(summary = "修改个人资料", description = "更新昵称、头像、手机号;商家可更新店铺名称与简介")
    public Result<AccountProfile> updateProfile(@Parameter(hidden = true) HttpServletRequest request,
                                                @RequestBody ProfileUpdateRequest body) {
        Long accountId = AuthContext.getUserId(request);
        String accountType = AuthContext.getAccountType(request);
        return Result.ok(authService.updateProfile(accountId, accountType, body));
    }

    @PostMapping("/auth/password")
    @Operation(summary = "修改密码", description = "校验原密码后更新为新密码")
    public Result<Void> changePassword(@Parameter(hidden = true) HttpServletRequest request,
                                       @Valid @RequestBody PasswordChangeRequest body) {
        Long accountId = AuthContext.getUserId(request);
        String accountType = AuthContext.getAccountType(request);
        authService.changePassword(accountId, accountType, body);
        return Result.ok();
    }
}
