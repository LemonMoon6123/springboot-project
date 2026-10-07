package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI Agent 管理端总览")
public class AgentAdminOverview {
    @Schema(description = "Agent 是否就绪(配置完整)", example = "true")
    private boolean ready;
    @Schema(description = "Agent 是否启用", example = "true")
    private boolean enabled;
    @Schema(description = "大模型供应商", example = "openai")
    private String provider;
    @Schema(description = "大模型 API 地址", example = "https://api.openai.com/v1")
    private String apiUrl;
    @Schema(description = "对话模型", example = "gpt-4o-mini")
    private String model;
    @Schema(description = "视觉模型", example = "gpt-4o")
    private String visionModel;
    @Schema(description = "API Key(脱敏)", example = "sk-***abc")
    private String apiKeyMasked;
    @Schema(description = "对话接口路径", example = "/ai/chat")
    private String chatEndpoint;
    @Schema(description = "视觉接口路径", example = "/ai/vision")
    private String visionEndpoint;
    @Schema(description = "会话存储类型", example = "memory")
    private String storeType;
    @Schema(description = "向量库类型", example = "simple")
    private String embeddingType;
    @Schema(description = "工具数量", example = "12")
    private int toolCount;
    @Schema(description = "RAG 文档数量", example = "5")
    private int ragDocCount;
    @Schema(description = "RAG 切片数量", example = "120")
    private int ragChunkCount;
    @Schema(description = "工具列表")
    private List<AgentToolView> tools = new ArrayList<>();
    @Schema(description = "RAG 文档列表")
    private List<RagDocView> ragDocs = new ArrayList<>();
}
