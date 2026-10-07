package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "发货请求")
public class ShipOrderRequest {
    /** 快递公司 */
    @Schema(description = "快递公司", example = "顺丰速运", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expressCompany;
    /** 物流单号 */
    @Schema(description = "物流单号", example = "SF1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    private String trackingNo;
    /** 备注 */
    @Schema(description = "发货备注", example = "易碎品请轻放")
    private String remark;
}
