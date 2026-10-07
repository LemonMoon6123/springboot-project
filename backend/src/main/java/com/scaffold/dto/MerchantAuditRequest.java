package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "商家审核请求")
public class MerchantAuditRequest {
    /** 1通过 2拒绝 */
    @Schema(description = "审核结果:1通过 2拒绝", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer auditStatus;

    @Schema(description = "审核备注", example = "资质齐全,审核通过")
    private String auditRemark;
}
