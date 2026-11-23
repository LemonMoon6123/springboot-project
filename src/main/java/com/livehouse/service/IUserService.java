package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.LoginFormDTO;
import com.livehouse.entity.Result;
import com.livehouse.entity.User;
import com.livehouse.vo.app.user.LoginTokenVo;

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
    LoginTokenVo login(LoginFormDTO loginForm);

    /**
     * 为已存在用户签发登录 token（与手机号验证码登录相同存储方式）
     */
    String issueLoginToken(User user);
}