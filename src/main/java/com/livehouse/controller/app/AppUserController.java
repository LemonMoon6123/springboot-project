package com.livehouse.controller.app;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.dto.UserDTO;
import com.livehouse.dto.app.WxLoginRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.User;
import com.livehouse.entity.UserInfo;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.UserInfoMapper;
import com.livehouse.service.IUserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.LOGIN_USER_KEY;
import static com.livehouse.utils.RedisConstants.LOGIN_USER_TTL;

@RestController
@RequestMapping("/app/user")
public class AppUserController {

    @Resource
    private IUserService userService;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LivehouseProperties livehouseProperties;

    @PostMapping("/login/wx")
    public Result wxLogin(@RequestBody WxLoginRequest request) {
        if (request == null || StrUtil.isBlank(request.getCode())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }

        String openid;
        if (livehouseProperties.getWxLogin().isMockOpenid()) {
            openid = livehouseProperties.getWxLogin().getMockOpenidPrefix() + request.getCode();
        } else {
            throw new StatusCodeException(StatusCode.WX_LOGIN_FAILED);
        }

        User user = userService.lambdaQuery().eq(User::getWxOpenid, openid).one();
        boolean needBindPhone = false;
        if (user == null) {
            user = new User();
            user.setWxOpenid(openid);
            user.setNickName("LH_" + openid.substring(Math.max(0, openid.length() - 8)));
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            userService.save(user);
            needBindPhone = StrUtil.isBlank(user.getPhone());

            UserInfo info = new UserInfo();
            info.setUserId(user.getId());
            info.setLevel(0);
            info.setCredits(0);
            info.setFans(0);
            info.setFollowee(0);
            info.setCreateTime(LocalDateTime.now());
            info.setUpdateTime(LocalDateTime.now());
            userInfoMapper.insert(info);
        } else {
            needBindPhone = StrUtil.isBlank(user.getPhone());
        }

        user.setLastLoginTime(LocalDateTime.now());
        userService.updateById(user);

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((n, v) -> v != null ? v.toString() : ""));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        Map<String, Object> userBrief = new HashMap<>();
        userBrief.put("id", String.valueOf(user.getId()));
        userBrief.put("nickName", user.getNickName());
        userBrief.put("icon", user.getIcon());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("needBindPhone", needBindPhone);
        data.put("user", userBrief);
        return Result.ok(data);
    }
}