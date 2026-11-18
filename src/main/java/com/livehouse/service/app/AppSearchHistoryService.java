package com.livehouse.service.app;

import java.util.List;
import java.util.Map;

public interface AppSearchHistoryService {

    List<String> list(Long userId);

    Map<String, Object> add(Long userId, String keyword);

    void removeOne(Long userId, String keyword);

    void clear(Long userId);
}