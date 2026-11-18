package com.livehouse.service.app;

import java.util.Map;

public interface AppCommentService {
    Map<String, Object> listComments(Long performanceId, String filter, int current, int size);
    void postComment(Long userId, Long performanceId, Long orderId, Integer rating, String content, String imagesJson);
    boolean hasCommentForOrder(Long orderId);
}