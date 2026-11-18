package com.livehouse.service.app;

import java.util.Map;

public interface AppMemberService {
    Map<String, Object> memberInfo(Long userId);
}