package com.scaffold.controller.admin;

import com.scaffold.agent.AgentAdminService;
import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.dto.AgentAdminOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/agent")
@Tag(name = "A16-管理端-AI Agent", description = "管理员查看 AI Agent 运行总览")
public class AdminAgentController {

    @Autowired
    private AgentAdminService agentAdminService;

    @GetMapping("/overview")
    @Operation(summary = "Agent 总览", description = "获取 AI Agent 工具调用、会话等总览数据;需管理员")
    public Result<AgentAdminOverview> overview(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(agentAdminService.overview());
    }
}
