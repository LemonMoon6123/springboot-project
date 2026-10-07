package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAG 知识库总览")
public class RagOverview {
    @Schema(description = "文档数量", example = "5")
    private int docCount;
    @Schema(description = "切片数量", example = "120")
    private int chunkCount;
    @Schema(description = "总字符数", example = "25600")
    private int totalChars;
    @Schema(description = "存储类型", example = "memory")
    private String storeType;
    @Schema(description = "向量库类型", example = "simple")
    private String embeddingType;
    @Schema(description = "文档列表")
    private List<RagDocView> docs = new ArrayList<>();
}
