package com.livehouse.service.app;

import java.util.Map;

public interface AppPayService {

    Map<String, Object> prepay(Long userId, Long orderId, String channel);

    Map<String, Object> handleWxNotify(Map<String, Object> body);

    void mockPay(Long userId, Long orderId);
}