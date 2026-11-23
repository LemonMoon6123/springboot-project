package com.livehouse.service.app;

import com.livehouse.vo.app.search.SearchHistoryAddVo;

import java.util.List;

public interface AppSearchHistoryService {

    List<String> list(Long userId);

    SearchHistoryAddVo add(Long userId, String keyword);

    void removeOne(Long userId, String keyword);

    void clear(Long userId);
}