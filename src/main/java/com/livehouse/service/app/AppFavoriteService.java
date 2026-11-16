package com.livehouse.service.app;

import java.util.Map;

public interface AppFavoriteService {

    Map<String, Object> add(Long userId, String targetType, Long targetId);

    Map<String, Object> remove(Long userId, String targetType, Long targetId);

    Map<String, Object> list(Long userId, String type, int current, int size);
}