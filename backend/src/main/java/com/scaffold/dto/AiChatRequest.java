package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 对话请求")
public class AiChatRequest {

    @Schema(description = "用户输入的消息内容", example = "我想买一部拍照好的手机,预算3000以内")
    private String message;

    /** 识图导购：已上传图片地址，如 /uploads/xxx.jpg */
    @Schema(description = "识图导购:已上传图片地址,如 /uploads/xxx.jpg", example = "/uploads/2026/product.jpg")
    private String imageUrl;

    /** Agent 多轮上下文（不含当前 message） */
    @Schema(description = "Agent 多轮上下文(不含当前 message),用于维持对话记忆")
    private List<AiChatTurn> history;

    @Data
    @Schema(description = "对话历史单轮")
    public static class AiChatTurn {

        @Schema(description = "角色:user(用户) | assistant(AI)", example = "user")
        private String role;

        @Schema(description = "消息内容", example = "推荐一款手机")
        private String content;
    }
}
