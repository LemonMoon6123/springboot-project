package com.livehouse.service.app;

import java.util.Map;

public interface AppRealnameService {

    Map<String, Object> get(Long userId);

    Map<String, Object> submit(Long userId, String realName, Integer idType, String idCard, String phone);
}