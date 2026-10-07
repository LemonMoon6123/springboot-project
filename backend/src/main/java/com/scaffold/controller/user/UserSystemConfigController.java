package com.scaffold.controller.user;

import com.scaffold.common.Result;
import com.scaffold.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user/system-config")
@Tag(name = "U11-用户端-系统配置", description = "获取前端公开的系统配置项")
public class UserSystemConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping
    @Operation(summary = "公开系统配置", description = "返回对前端公开的系统配置键值对")
    public Result<Map<String, String>> get() {
        return Result.ok(sysConfigService.getPublicConfig());
    }
}
