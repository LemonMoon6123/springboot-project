package com.livehouse.service.app;

import com.livehouse.dto.app.OrderCreateRequest;
import com.livehouse.dto.app.OrderPreviewRequest;

import java.util.List;
import java.util.Map;

public interface AppOrderService {

    Map<String, Object> preview(Long userId, OrderPreviewRequest request);

    Map<String, Object> create(Long userId, OrderCreateRequest request);

    Map<String, Object> list(Long userId, String og, Integer entryStatus, int current, int size);

    Map<String, Object> detail(Long userId, Long orderId);

    void cancel(Long userId, Long orderId, String reason);

    void batchDelete(Long userId, List<Long> orderIds);

    Map<String, Object> refund(Long userId, Long orderId, String reason);
}