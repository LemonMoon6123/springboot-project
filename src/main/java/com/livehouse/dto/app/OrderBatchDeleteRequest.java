package com.livehouse.dto.app;

import lombok.Data;

import java.util.List;

@Data
public class OrderBatchDeleteRequest {

    private List<Long> orderIds;
}