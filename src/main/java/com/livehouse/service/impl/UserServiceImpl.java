package com.livehouse.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.dto.LoginFormDTO;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.User;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.util.app.UserLoginSupport;
import com.livehouse.vo.app.user.LoginTokenVo;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.entity.UserInfo;
import com.livehouse.mapper.UserInfoMapper;
import com.livehouse.mapper.UserMapper;
import com.livehouse.service.IUserService;
import com.livehouse.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.*;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private LivehouseProperties livehouseProperties;
    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail(StatusCode.PHONE_FORMAT_ERROR);
        }
        
        // 3. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 写入 Redis，登录时比对
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("短信验证码已写入 Redis key={}{}，TTL={}min，验证码：{}",
                LOGIN_CODE_KEY, phone, LOGIN_CODE_TTL, code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public LoginTokenVo login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new StatusCodeException(StatusCode.PHONE_FORMAT_ERROR);
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            throw new StatusCodeException(StatusCode.SMS_CODE_ERROR);
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        } else {
            ensureUserInfo(user.getId());
        }
        return UserLoginSupport.issueToken(user, stringRedisTemplate);
    }

    @Override
    public String issueLoginToken(User user) {
        return UserLoginSupport.issueToken(user, stringRedisTemplate).getToken();
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户_" + RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        // 2.保存用户
        save(user);
        ensureUserInfo(user.getId());
        return user;
    }

    /** 与微信登录一致：保证 tb_user_info 存在，否则「我的」积分/等级等全为 0 */
    private void ensureUserInfo(Long userId) {
        if (userId == null || userInfoMapper.selectById(userId) != null) {
            return;
        }
        UserInfo info = new UserInfo();
        info.setUserId(userId);
        info.setLevel(0);
        info.setCredits(0);
        info.setTotalCredits(0);
        info.setWatchCount(0);
        info.setFollowee(0);
        info.setCreateTime(LocalDateTime.now());
        info.setUpdateTime(LocalDateTime.now());
        userInfoMapper.insert(info);
    }
}