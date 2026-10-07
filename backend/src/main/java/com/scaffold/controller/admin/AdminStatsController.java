package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/stats")
@Tag(name = "A18-管理端-统计总览", description = "管理员查看全平台运营统计数据")
public class AdminStatsController {

    @Autowired
    private StatsService statsService;

    @GetMapping
    @Operation(summary = "运营统计", description = "获取用户/商家/订单/销售/商品等运营汇总数据;需管理员")
    public Result<Map<String, Object>> stats(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(statsService.adminStats());
    }
}
