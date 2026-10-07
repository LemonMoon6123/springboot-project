package com.scaffold.common;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {

    private AuthContext() {
    }

    public static Long getUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Integer id) {
            return id.longValue();
        }
        return null;
    }

    public static String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }

    public static String getRole(HttpServletRequest request) {
        return (String) request.getAttribute("role");
    }

    public static String getAccountType(HttpServletRequest request) {
        return (String) request.getAttribute("accountType");
    }

    public static void requireAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(getAccountType(request)) && !"ADMIN".equals(getRole(request))) {
            throw new BusinessException(403, "无管理员权限");
        }
    }

    public static Long requireLogin(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    /** 商城用户操作，仅普通 USER（不含商家） */
    public static Long requireUser(HttpServletRequest request) {
        Long userId = requireLogin(request);
        String accountType = getAccountType(request);
        if ("ADMIN".equals(accountType) || "MERCHANT".equals(accountType)) {
            throw new BusinessException(403, "请使用用户账号操作");
        }
        if ("MERCHANT".equals(getRole(request))) {
            throw new BusinessException(403, "请使用用户账号操作");
        }
        return userId;
    }

    /** 已审核通过的商家 */
    public static Long requireMerchant(HttpServletRequest request) {
        Long userId = requireLogin(request);
        if (!"MERCHANT".equals(getAccountType(request)) && !"MERCHANT".equals(getRole(request))) {
            throw new BusinessException(403, "无商家权限");
        }
        return userId;
    }

    public static Long requireAdminOrMerchant(HttpServletRequest request) {
        Long userId = requireLogin(request);
        String accountType = getAccountType(request);
        String role = getRole(request);
        boolean ok = "ADMIN".equals(accountType) || "ADMIN".equals(role)
                || "MERCHANT".equals(accountType) || "MERCHANT".equals(role);
        if (!ok) {
            throw new BusinessException(403, "无权限");
        }
        return userId;
    }
}
