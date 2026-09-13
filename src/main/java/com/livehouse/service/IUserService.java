package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.LoginFormDTO;
import com.livehouse.dto.Result;
import com.livehouse.entity.User;

/**
 * 用户服务接口
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @return 发送结果
     */
    Result sendCode(String phone);

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm);
}