package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "充值请求")
public class RechargeRequest {

    @Schema(description = "充值金额", example = "100.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;
}
