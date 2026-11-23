package com.livehouse.interceptor;

import com.livehouse.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * 功能：验证需要登录权限的接口是否已登录
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        // 管理后台（含数据大屏、通用 CRUD）不要求 C 端登录
        if (uri != null && (uri.startsWith("/admin") || uri.startsWith("/admin/"))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if (uri.matches(".*/app/show/\\d+/comments") || "/app/points/shop".equals(uri)) {
                return true;
            }
        }
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}