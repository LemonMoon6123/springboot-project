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
@Schema(description = "RAG 文档视图")
public class RagDocView {
    @Schema(description = "文件名", example = "product-faq.md")
    private String filename;
    @Schema(description = "标题", example = "商品常见问题")
    private String title;
    @Schema(description = "摘要", example = "汇总商品咨询高频问题")
    private String summary;
    @Schema(description = "正文内容", example = "...")
    private String content;
    @Schema(description = "切片数量", example = "8")
    private int chunkCount;
    @Schema(description = "字符数", example = "2048")
    private int charCount;
    /** builtin=内置 classpath；upload=后台上传 */
    @Schema(description = "来源:builtin=内置 classpath;upload=后台上传", example = "builtin")
    private String origin;
    @Schema(description = "是否为用户上传", example = "false")
    private boolean uploaded;
    @Schema(description = "切片列表")
    private List<RagChunkView> chunks = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "RAG 文档切片")
    public static class RagChunkView {
        @Schema(description = "切片序号", example = "0")
        private int index;
        @Schema(description = "切片文本", example = "...")
        private String text;
        @Schema(description = "切片字符数", example = "256")
        private int charCount;
    }
}
