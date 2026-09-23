package com.livehouse.config;

import com.livehouse.interceptor.RateLimitInterceptor;
import com.livehouse.interceptor.LoginInterceptor;
import com.livehouse.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

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

    @Value("${livehouse.upload.dir:frontend-livehouse/uploads}")
    private String uploadDir;

    @Value("${livehouse.upload.url-prefix:/uploads/}")
    private String urlPrefix;

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
                        "/uploads/**",  // 静态资源访问，不需要登录
                        "/ticket/verify",       // 电子票核销（无路径参数形式）
                        "/ticket/verify/**",    // 电子票核销（路径参数形式）
                        "/ticket/detail",       // 电子票详情（Query参数形式）
                        "/ticket/detail/**"
                ).order(1);
    }

    /**
     * 配置静态资源映射
     * 将 /uploads/** 路径映射到上传目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 解析上传目录的绝对路径
        String absoluteUploadPath;
        if (Paths.get(uploadDir).isAbsolute()) {
            absoluteUploadPath = "file:" + uploadDir + "/";
        } else {
            absoluteUploadPath = "file:" + System.getProperty("user.dir") + "/" + uploadDir + "/";
        }
        
        // 配置静态资源映射：/uploads/** -> 上传目录
        registry.addResourceHandler(urlPrefix + "**")
                .addResourceLocations(absoluteUploadPath)
                .setCachePeriod(3600 * 24 * 7); // 缓存7天
    }
}