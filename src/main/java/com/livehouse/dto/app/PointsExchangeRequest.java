package com.livehouse.dto.app;

import lombok.Data;

@Data
public class PointsExchangeRequest {

    private Long goodsId;
    private String idempotentKey;
}