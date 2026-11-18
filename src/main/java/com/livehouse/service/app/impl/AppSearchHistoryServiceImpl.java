package com.livehouse.service.app.impl;

import cn.hutool.core.util.StrUtil;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppSearchHistoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 搜索历史：登录用户存Redis，未登录用户存LocalStorage。
 */
@Service
public class AppSearchHistoryServiceImpl implements AppSearchHistoryService {

    private static final int MAX_SIZE = 20;
    private static final long TTL_DAYS = 180;
    private static final String KEY_PREFIX = "search:history:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<String> list(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> raw = stringRedisTemplate.opsForList().range(key(userId), 0, MAX_SIZE - 1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> add(Long userId, String keyword) {
        if (userId == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        String w = keyword == null ? "" : keyword.trim();
        if (w.isEmpty()) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        String k = key(userId);
        stringRedisTemplate.opsForList().remove(k, 0, w);
        stringRedisTemplate.opsForList().leftPush(k, w);
        stringRedisTemplate.opsForList().trim(k, 0, MAX_SIZE - 1);
        stringRedisTemplate.expire(k, TTL_DAYS, TimeUnit.DAYS);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", list(userId));
        return data;
    }

    @Override
    public void removeOne(Long userId, String keyword) {
        if (userId == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        stringRedisTemplate.opsForList().remove(key(userId), 0, keyword.trim());
    }

    @Override
    public void clear(Long userId) {
        if (userId == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        stringRedisTemplate.delete(key(userId));
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}