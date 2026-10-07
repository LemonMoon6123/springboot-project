package com.scaffold.controller;

import com.scaffold.agent.MallAgentService;
import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.dto.AiChatRequest;
import com.scaffold.dto.AiGuideResponse;
import com.scaffold.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@Tag(name = "A02-公共-AI智能助手", description = "AI 导购、多模态识图、Agent 工具调用、智能客服")
public class AiController {

    @Autowired
    private AiService aiService;
    @Autowired
    private MallAgentService mallAgentService;


    @PostMapping("/guide")
    @Operation(summary = "AI 导购咨询", description = "用户输入自然语言,AI 推荐合适商品")
    public Result<AiGuideResponse> guide(@RequestBody AiChatRequest request) {
        return Result.ok(aiService.guide(request.getMessage()));
    }

    /**
     * Spring AI Agent：导购 + RAG + 购物车/订单 Tool Calling。
     * 可选登录：未登录仅导购与答疑；加购/下单需 JWT。
     */
    @PostMapping("/agent")
    @Operation(summary = "AI Agent 导购助手", description = "Spring AI Agent:导购 + RAG 知识库 + 购物车/订单工具调用;未登录仅导购与答疑,加购/下单需 JWT")
    public Result<AiGuideResponse> agent(@Parameter(hidden = true) HttpServletRequest request, @RequestBody AiChatRequest body) {
        Long userId = AuthContext.getUserId(request);
        return Result.ok(mallAgentService.chat(body.getMessage(), userId, body.getHistory()));
    }

    /** 上传图片识图导购（传已上传后的 imageUrl，可附带补充说明） */
    @PostMapping("/guide/vision")
    @Operation(summary = "图片识图导购", description = "传入已上传图片地址,AI 识别图片内容并推荐商品")
    public Result<AiGuideResponse> guideByImage(@RequestBody AiChatRequest request) {
        return Result.ok(aiService.guideByImage(request.getImageUrl(), request.getMessage()));
    }

    @PostMapping("/service")
    @Operation(summary = "AI 智能客服", description = "用户向 AI 客服提问,返回自动回复;需登录")
    public Result<Map<String, String>> customerService(@Parameter(hidden = true) HttpServletRequest request,
                                                       @RequestBody AiChatRequest body) {
        Long userId = AuthContext.requireUser(request);
        String reply = aiService.customerService(body.getMessage(), userId);
        return Result.ok(Map.of("reply", reply));
    }
}
