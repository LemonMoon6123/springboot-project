package com.livehouse.service.app;

import java.util.List;
import java.util.Map;

public interface AppPointsService {
    List<Map<String, Object>> shop(Long userId);
    Map<String, Object> logs(Long userId, int current, int size);
    Map<String, Object> exchange(Long userId, Long goodsId, String idempotentKey);
}