package com.livehouse.service.app;

import java.util.Map;

public interface AppMessageService {

    Map<String, Object> list(Long userId, String type, int current, int size);

    void readAll(Long userId);

    int countUnread(Long userId);

    void insertMessage(Long userId, String type, String title, String content, String bizType, String bizId);
}