package com.livehouse.config;

import com.livehouse.interceptor.RateLimitInterceptor;
import com.livehouse.utils.LoginInterceptor;
import com.livehouse.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 * 配置拦截器的注册和拦截规则
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP限流拦截器：最高优先级，拦截秒杀请求进行限流
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/seckill/**")    // 只拦截秒杀请求
                .order(-1);

        // Token刷新拦截器：所有请求都经过，刷新token过期时间
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).order(0);
                
        // 登录拦截器：拦截需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/show/**",
                        "/venue/**",
                        "/upload/**",
                        "/ticket/verify/**",    // 电子票核销（工作人员使用）
                        "/ticket/detail/**"
                ).order(1);
    }
}