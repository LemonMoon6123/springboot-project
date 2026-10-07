package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "问答回复请求")
public class QaAnswerRequest {

    @Schema(description = "回复内容", example = "这款手机支持双卡双待", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answer;
}
