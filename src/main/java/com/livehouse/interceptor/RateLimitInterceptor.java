package com.livehouse.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livehouse.dto.Result;
import com.livehouse.utils.RateLimitExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * IP限流拦截器
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RateLimitExecutor rateLimitExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只拦截秒杀请求
        String requestURI = request.getRequestURI();
        //  并不是所有的请求都是秒杀请求，该拦截器会在第一个执行。
        if (!requestURI.startsWith("/seckill/")) {
            return true;
        }

        // 获取客户端IP
        String clientIp = getClientIp(request);
        
        // 执行限流检查：10秒内最多5次请求
        boolean allowed = rateLimitExecutor.checkRateLimit(clientIp, 10, 5);
        
        if (!allowed) {
            // 超出限制，返回429错误
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            
            Result result = Result.fail("请求过于频繁，请稍后再试");
            String jsonResponse = objectMapper.writeValueAsString(result);
            response.getWriter().write(jsonResponse);
            
            log.warn("IP限流拦截，IP：{}，URI：{}", clientIp, requestURI);
            return false;
        }

        return true;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 如果是多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        
        return ip;
    }
}