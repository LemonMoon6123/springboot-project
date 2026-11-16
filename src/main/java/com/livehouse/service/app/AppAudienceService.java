package com.livehouse.service.app;

import com.livehouse.dto.app.AudienceCreateRequest;
import com.livehouse.dto.app.AudienceUpdateRequest;

import java.util.Map;

public interface AppAudienceService {

    Map<String, Object> list(Long userId);

    Map<String, Object> create(Long userId, AudienceCreateRequest request);

    Map<String, Object> update(Long userId, Long id, AudienceUpdateRequest request);

    Map<String, Object> delete(Long userId, Long id);

    Map<String, Object> setDefault(Long userId, Long id);
}