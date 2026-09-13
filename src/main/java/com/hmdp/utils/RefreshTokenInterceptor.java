package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Auther: shaolei
 * @Date: 2026/9/2-18:47
 * @Description：所有请求必经过这里，有四个功能。
 * 1.为登录过且用户信息未过期（redis中token对应的用户信息仍存在）的请求作用户信息过期时间的刷新，
 * 目的是维持活跃用户的用户信息不被销毁，因expire方法到过期时长就销毁key及数据。
 *
 * 2.为未登录的请求放行，LoginInterceptor也不拦截。
 * 3.为用户信息过期（redis中token对应的用户信息不存在）（也代表不活跃，登录状态过期）的请求放行，专门由LoginInterceptor拦截器负责拦截。
 * 4.解决session在集群服务器下共享数据的问题（因为现在只有一台Redis服务器，所有请求的用户数据都保存在这一台服务器中）。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token令牌。
        String token = request.getHeader("authorization"); // 前端代码中设置请求头key为这个名字，value值就是登录后返回的token令牌。
        // 2.校验token是否为空。(为空，代表未登录，放行)
        if(StrUtil.isBlank(token)){
           return true;
        }
        // 3.从redis中获取用户信息。
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        // 4.校验用户信息是否存在，不存在代表用户信息过期了，redis没有用户信息，但浏览器仍然存有这个用户信息的token令牌，也就是key。
        if(userMap.isEmpty()){
            return true; // 用户信息过期，那就放行，会被LoginInterceptor拦截器拦截住。
        }
        // 走到这里，代表用户登陆过，因此浏览器有携带token，且redis中存有用户信息。
        // 5.关键：只要redis中由该用户信息，就保存这个用户信息于ThreadLocal变量中，如果去掉这步，当有需要校验登录状态的请求过来时就无法通过，会被LoginInterceptor拦截。
        UserDTO userDTO = new UserDTO();
        UserDTO userMessage = BeanUtil.fillBeanWithMap(userMap,userDTO,false);
        UserHolder.saveUser(userMessage);
        // 6.刷新用户信息过期时间
        redisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 在controller方法执行结束后，返回视图后销毁ThreadLocal中开辟的内存空间，销毁线程对应的用户信息，防止内存泄漏。
        UserHolder.removeUser();
    }

}
