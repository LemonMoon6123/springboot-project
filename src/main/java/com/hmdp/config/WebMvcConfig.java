package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * @Auther: shaolei
 * @Date: 2026/9/1-21:59
 * @Description：注册并设置拦截器的拦截先后顺序，利用order进行排序，数字越大代表优先级越低。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/blog/hot",
                        "/upload/**"
                        ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).order(0);
    }
}
