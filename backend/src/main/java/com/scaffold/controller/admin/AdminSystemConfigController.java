package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/system-config")
@Tag(name = "A24-管理端-系统配置", description = "管理员系统名称/Logo 等公开配置")
public class AdminSystemConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping
    @Operation(summary = "获取公开配置", description = "获取系统名称、Logo 等公开配置;需管理员")
    public Result<Map<String, String>> get(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysConfigService.getPublicConfig());
    }

    @PutMapping
    @Operation(summary = "更新公开配置", description = "更新系统名称、Logo 等公开配置;需管理员")
    public Result<Map<String, String>> update(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, String> body) {
        AuthContext.requireAdmin(request);
        String systemName = body == null ? null : body.get("systemName");
        String systemLogo = body == null ? null : body.get("systemLogo");
        return Result.ok(sysConfigService.updatePublicConfig(systemName, systemLogo));
    }
}
