package com.livehouse.dto.app;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    private Long showId;
    private Long ticketTypeId;
    private Integer quantity;
    private List<Long> audienceIds;
    private String idempotentKey;
}