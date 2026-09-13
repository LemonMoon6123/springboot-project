package com.livehouse.config;

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器：拦截需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",           // 获取验证码
                        "/user/login",          // 用户登录
                        "/show/**",             // 演出信息查询（允许游客访问）
                        "/venue/**",            // 场馆信息查询
                        "/upload/**"            // 文件上传
                ).order(1);
                
        // Token刷新拦截器：所有请求都经过，刷新token过期时间
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).order(0);
    }
}