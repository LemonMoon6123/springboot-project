package com.livehouse.dto.app;

import lombok.Data;

@Data
public class PayPrepayRequest {

    private Long orderId;
    private String channel;
}