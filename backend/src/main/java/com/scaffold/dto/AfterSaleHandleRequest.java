package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "售后处理请求")
public class AfterSaleHandleRequest {
    /** true同意 false拒绝 */
    @Schema(description = "是否同意:true同意 false拒绝", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean approve;

    @Schema(description = "处理回复说明", example = "同意退款")
    private String reply;
}
