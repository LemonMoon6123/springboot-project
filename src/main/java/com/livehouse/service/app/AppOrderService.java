package com.livehouse.service.app;

import com.livehouse.dto.app.OrderCreateRequest;
import com.livehouse.dto.app.OrderPreviewRequest;
import com.livehouse.vo.app.order.OrderCreateResultVo;
import com.livehouse.vo.app.order.OrderListItemVo;
import com.livehouse.vo.app.order.OrderListVo;
import com.livehouse.vo.app.order.OrderPreviewVo;
import com.livehouse.vo.app.order.OrderRefundApplyVo;

import java.util.List;

public interface AppOrderService {

    OrderPreviewVo preview(Long userId, OrderPreviewRequest request);

    OrderCreateResultVo create(Long userId, OrderCreateRequest request);

    OrderListVo list(Long userId, String og, Integer entryStatus, int current, int size);

    OrderListItemVo detail(Long userId, Long orderId);

    void cancel(Long userId, Long orderId, String reason);

    void batchDelete(Long userId, List<Long> orderIds);

    OrderRefundApplyVo refund(Long userId, Long orderId, String reason);
}