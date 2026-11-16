package com.livehouse.dto.app;

import lombok.Data;

import java.util.List;

@Data
public class OrderPreviewRequest {
    private Long showId;
    private Long ticketTypeId;
    private Integer quantity;
    private List<Long> audienceIds;
}