package com.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建订单请求")
public class CreateOrderRequest {

    @Schema(description = "收货地址 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long addressId;

    @Schema(description = "购物车项 ID 列表(也兼容 cartIds 字段名)", example = "[1,2,3]", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("cartIds")
    private List<Long> cartItemIds;
}
