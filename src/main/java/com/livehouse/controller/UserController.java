package com.livehouse.controller;

import com.livehouse.dto.LoginFormDTO;
import com.livehouse.dto.Result;
import com.livehouse.dto.UserDTO;
import com.livehouse.dto.UserProfileDTO;
import com.livehouse.entity.User;
import com.livehouse.entity.UserInfo;
import com.livehouse.mapper.UserInfoMapper;
import com.livehouse.service.IUserService;
import com.livehouse.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private UserInfoMapper userInfoMapper;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 当前登录用户资料
     */
    @GetMapping("/profile")
    public Result profile() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("未登录");
        }
        User user = userService.getById(currentUser.getId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserInfo userInfo = userInfoMapper.selectById(currentUser.getId());
        return Result.ok(buildProfile(user, userInfo));
    }

    /**
     * 更新当前登录用户资料
     */
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody UserProfileDTO profile) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("未登录");
        }

        Long userId = currentUser.getId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        if (profile.getNickName() != null) {
            user.setNickName(profile.getNickName());
        }
        if (profile.getIcon() != null) {
            user.setIcon(profile.getIcon());
        }
        if (profile.getPassword() != null && !profile.getPassword().trim().isEmpty()) {
            user.setPassword(profile.getPassword());
        }
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setUserId(userId);
            userInfo.setFans(0);
            userInfo.setFollowee(0);
            userInfo.setCredits(0);
            userInfo.setLevel(0);
            userInfo.setCreateTime(LocalDateTime.now());
        }
        userInfo.setCity(profile.getCity());
        userInfo.setIntroduce(profile.getIntroduce());
        userInfo.setGender(profile.getGender());
        userInfo.setBirthday(profile.getBirthday());
        userInfo.setUpdateTime(LocalDateTime.now());

        if (userInfoMapper.selectById(userId) == null) {
            userInfoMapper.insert(userInfo);
        } else {
            userInfoMapper.updateById(userInfo);
        }

        return Result.ok(buildProfile(user, userInfo));
    }

    private UserProfileDTO buildProfile(User user, UserInfo userInfo) {
        UserProfileDTO profile = new UserProfileDTO();
        profile.setId(user.getId());
        profile.setPhone(user.getPhone());
        profile.setNickName(user.getNickName());
        profile.setIcon(user.getIcon());
        if (userInfo != null) {
            profile.setCity(userInfo.getCity());
            profile.setIntroduce(userInfo.getIntroduce());
            profile.setGender(userInfo.getGender());
            profile.setBirthday(userInfo.getBirthday());
        }
        return profile;
    }
}