package com.livehouse.util.app;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.livehouse.dto.UserDTO;
import com.livehouse.entity.User;
import com.livehouse.vo.app.user.LoginTokenVo;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.LOGIN_USER_KEY;
import static com.livehouse.utils.RedisConstants.LOGIN_USER_TTL;

/** 登录 token 签发（Redis Hash 存 UserDTO 字符串字段） */
public final class UserLoginSupport {

    private UserLoginSupport() {
    }

    public static LoginTokenVo issueToken(User user, StringRedisTemplate redis) {
        String token = cn.hutool.core.lang.UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : ""));
        String tokenKey = LOGIN_USER_KEY + token;
        redis.opsForHash().putAll(tokenKey, userMap);
        redis.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        LoginTokenVo vo = new LoginTokenVo();
        vo.setToken(token);
        return vo;
    }
}