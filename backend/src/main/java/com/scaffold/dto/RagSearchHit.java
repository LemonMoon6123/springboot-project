package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAG 检索命中")
public class RagSearchHit {
    @Schema(description = "命中文件名", example = "product-faq.md")
    private String filename;
    @Schema(description = "命中文本片段", example = "...")
    private String text;
    @Schema(description = "相似度得分", example = "0.86")
    private Double score;
}
