package com.livehouse.util.app;

import cn.hutool.core.util.StrUtil;
import com.livehouse.dto.app.FavoriteTargetRequest;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;

public final class FavoriteParamsResolver {

    private FavoriteParamsResolver() {
    }

    public static Resolved resolve(FavoriteTargetRequest body, String targetTypeParam, String targetIdParam) {
        String type = targetTypeParam;
        Object idRaw = targetIdParam;
        if (body != null) {
            if (StrUtil.isBlank(type) && body.getTargetType() != null) {
                type = body.getTargetType();
            }
            if (idRaw == null && body.getTargetId() != null) {
                idRaw = body.getTargetId();
            }
        }
        type = type == null ? null : type.trim();
        Long targetId = parseTargetId(idRaw);
        if (StrUtil.isBlank(type) || targetId == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "targetType 或 targetId 无效");
        }
        return new Resolved(type, targetId);
    }

    private static Long parseTargetId(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "undefined".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final class Resolved {
        public final String targetType;
        public final Long targetId;

        public Resolved(String targetType, Long targetId) {
            this.targetType = targetType;
            this.targetId = targetId;
        }
    }
}