package com.livehouse.controller.app;

import com.livehouse.dto.UserDTO;
import com.livehouse.dto.app.BindPhoneRequest;
import com.livehouse.dto.app.StaffDemoLoginRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppUserAuthService;
import com.livehouse.service.app.AppUserCenterService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/user")
public class AppUserController {

    @Resource
    private AppUserAuthService appUserAuthService;
    @Resource
    private AppUserCenterService appUserCenterService;

    @GetMapping("/center")
    public Result center() {
        UserDTO loginUser = UserHolder.getUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appUserCenterService.center(loginUser.getId()));
    }

    @PostMapping("/bind-phone")
    public Result bindPhone(@RequestBody BindPhoneRequest request) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return Result.ok(appUserAuthService.bindPhone(request, userId));
    }

    @PostMapping("/login/staff-demo")
    public Result staffDemoLogin(@RequestBody StaffDemoLoginRequest request) {
        return Result.ok(appUserAuthService.staffDemoLogin(request));
    }
}