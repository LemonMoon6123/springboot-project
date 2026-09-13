package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Auther: shaolei
 * @Date: 2026/9/1-21:48
 * @Description：为需要验证登陆状态的请求检查其ThreadLocal变量中所保存的用户信息是否存在作为登录信息是否过期的情况进行拦截判断。
 *
 * 可能还有一种特殊情况，就是用户主动删除浏览器中保存的token，之后再去访问需要验证登陆状态的页面时，会来到这，此时ThreadLocal
 * 中一定没有用户信息，那也活该被拦截。
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只做一件事，校验ThreadLocal中是否存有用户信息。
        if(UserHolder.getUser() == null){
            return false;
        }
        return true;
    }
}
