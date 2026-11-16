package com.livehouse.service.app;

import java.util.List;
import java.util.Map;

public interface AppVerifyService {

    List<Map<String, Object>> sessions(Long userId);

    Map<String, Object> scan(Long operatorUserId, Long showId, String ticketCode, String shortCode, String gate);

    Map<String, Object> stats(Long userId, Long showId);
}