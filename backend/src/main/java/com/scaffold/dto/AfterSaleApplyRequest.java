package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "售后申请请求")
public class AfterSaleApplyRequest {
    /** 1仅退款 2退货退款 3换货 */
    @Schema(description = "售后类型:1仅退款 2退货退款 3换货", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer type;

    @Schema(description = "申请原因", example = "商品与描述不符", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
