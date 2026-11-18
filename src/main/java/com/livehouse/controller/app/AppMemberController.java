package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppMemberService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/member")
public class AppMemberController {

    @Resource
    private AppMemberService appMemberService;

    @GetMapping("/info")
    public Result info() {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(appMemberService.memberInfo(userId));
    }
}