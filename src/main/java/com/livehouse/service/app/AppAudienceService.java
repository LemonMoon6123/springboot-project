package com.livehouse.service.app;

import com.livehouse.dto.app.AudienceCreateRequest;
import com.livehouse.dto.app.AudienceUpdateRequest;
import com.livehouse.vo.app.audience.AudienceListVo;

public interface AppAudienceService {

    AudienceListVo list(Long userId);

    AudienceListVo create(Long userId, AudienceCreateRequest request);

    AudienceListVo update(Long userId, Long id, AudienceUpdateRequest request);

    AudienceListVo delete(Long userId, Long id);

    AudienceListVo setDefault(Long userId, Long id);
}