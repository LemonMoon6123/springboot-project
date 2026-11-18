package com.livehouse.controller.app;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.dto.UserDTO;
import com.livehouse.dto.app.BindPhoneRequest;
import com.livehouse.dto.app.WxLoginRequest;
import com.livehouse.service.app.AppUserCenterService;
import com.livehouse.utils.RegexUtils;
import org.springframework.web.bind.annotation.GetMapping;
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

import static com.livehouse.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.livehouse.utils.RedisConstants.LOGIN_USER_KEY;
import static com.livehouse.utils.RedisConstants.LOGIN_USER_TTL;
import com.livehouse.utils.UserHolder;

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
    @Resource
    private AppUserCenterService appUserCenterService;

    @GetMapping("/center")
    public Result center() {
        return Result.ok(appUserCenterService.center(UserHolder.getUser().getId()));
    }

    @PostMapping("/bind-phone")
    public Result bindPhone(@RequestBody BindPhoneRequest request) {
        if (request == null || StrUtil.isBlank(request.getPhone()) || StrUtil.isBlank(request.getCode())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        if (RegexUtils.isPhoneInvalid(request.getPhone())) {
            throw new StatusCodeException(StatusCode.PHONE_FORMAT_ERROR);
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + request.getPhone());
        if (cacheCode == null || !cacheCode.equals(request.getCode())) {
            throw new StatusCodeException(StatusCode.SMS_CODE_ERROR);
        }
        User exists = userService.lambdaQuery().eq(User::getPhone, request.getPhone()).one();
        Long userId;
        if (StrUtil.isNotBlank(request.getTempToken())) {
            String json = stringRedisTemplate.opsForValue().get("login:temp:" + request.getTempToken());
            if (json == null) {
                throw new StatusCodeException(StatusCode.UNAUTHORIZED);
            }
            userId = Long.parseLong(json);
        } else {
            userId = UserHolder.getUser().getId();
        }
        if (exists != null && !exists.getId().equals(userId)) {
            throw new StatusCodeException(StatusCode.PHONE_ALREADY_BOUND);
        }
        User user = userService.getById(userId);
        user.setPhone(request.getPhone());
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((n, v) -> v != null ? v.toString() : ""));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        return Result.ok(data);
    }

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
        if (needBindPhone) {
            String tempToken = UUID.randomUUID().toString(true);
            stringRedisTemplate.opsForValue().set("login:temp:" + tempToken,
                    String.valueOf(user.getId()), 30, TimeUnit.MINUTES);
            data.put("tempToken", tempToken);
        }
        return Result.ok(data);
    }
}