package com.livehouse.service.app;

import com.livehouse.dto.app.AudienceCreateRequest;

import java.util.Map;

public interface AppAudienceService {

    Map<String, Object> list(Long userId);

    Map<String, Object> create(Long userId, AudienceCreateRequest request);
}