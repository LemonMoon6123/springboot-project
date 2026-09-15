package com.livehouse.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.livehouse.dto.UserDTO;
import com.livehouse.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.LOGIN_USER_KEY;
import static com.livehouse.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Token刷新拦截器
 * 功能：
 * 1. 刷新活跃用户的token过期时间
 * 2. 将用户信息保存到ThreadLocal中
 * 3. 为未登录用户放行，由LoginInterceptor负责权限控制
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token令牌
        String token = request.getHeader("authorization");
        
        // 2.校验token是否为空（为空代表未登录，放行）
        if(StrUtil.isBlank(token)){
           return true;
        }
        
        // 3.从redis中获取用户信息
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        
        // 4.校验用户信息是否存在
        if(userMap.isEmpty()){
            return true; // 用户信息过期，放行，由LoginInterceptor拦截
        }
        
        // 5.保存用户信息到ThreadLocal
        UserDTO userDTO = new UserDTO();
        UserDTO userMessage = BeanUtil.fillBeanWithMap(userMap, userDTO, false);
        UserHolder.saveUser(userMessage);
        
        // 6.刷新用户信息过期时间
        redisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        
        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal，防止内存泄漏
        UserHolder.removeUser();
    }
}