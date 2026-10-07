package com.scaffold.security;

import com.scaffold.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 可匿名访问；若携带 token 则解析用户信息（用于个性化推荐等） */
    private static final String[] OPTIONAL_AUTH_PATTERNS = {
            "/user/categories",
            "/user/categories/**",
            "/user/products",
            "/user/products/**",
            "/user/shops",
            "/user/shops/**",
            "/user/announcements",
            "/user/announcements/**",
            "/user/banners",
            "/user/system-config",
            "/ai/guide",
            "/ai/guide/vision",
            "/ai/agent"
    };

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        boolean optional = isOptional(path);
        String authHeader = request.getHeader("Authorization");
        boolean hasBearer = authHeader != null && authHeader.startsWith("Bearer ");

        if (!hasBearer) {
            if (optional) {
                return true;
            }
            throw new BusinessException(401, "未登录或 token 无效");
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            if (optional) {
                return true;
            }
            throw new BusinessException(401, "登录已过期，请重新登录");
        }

        request.setAttribute("username", jwtUtils.getUsername(token));
        request.setAttribute("userId", jwtUtils.getUserId(token));
        request.setAttribute("role", jwtUtils.getRole(token));
        request.setAttribute("accountType", jwtUtils.getAccountType(token));
        return true;
    }

    private boolean isOptional(String path) {
        for (String pattern : OPTIONAL_AUTH_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
