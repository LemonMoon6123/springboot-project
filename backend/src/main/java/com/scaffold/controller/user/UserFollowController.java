package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.entity.ShopFollow;
import com.scaffold.service.ShopFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/follows")
@Tag(name = "U07-用户端-店铺关注", description = "关注列表、关注/取关、关注状态检查")
public class UserFollowController {

    @Autowired
    private ShopFollowService shopFollowService;

    @GetMapping
    @Operation(summary = "关注列表", description = "查询当前用户关注的店铺;需登录")
    public Result<List<ShopFollow>> list(@Parameter(hidden = true) HttpServletRequest request) {
        return Result.ok(shopFollowService.listByUser(AuthContext.requireUser(request)));
    }

    @PostMapping
    @Operation(summary = "关注店铺", description = "关注指定店铺;需登录")
    public Result<ShopFollow> follow(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long merchantId = body.get("merchantId") == null ? null : Long.valueOf(body.get("merchantId").toString());
        return Result.ok(shopFollowService.follow(AuthContext.requireUser(request), merchantId));
    }

    @DeleteMapping("/{merchantId}")
    @Operation(summary = "取消关注", description = "取消关注指定店铺;需登录")
    public Result<Void> unfollow(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long merchantId) {
        shopFollowService.unfollow(AuthContext.requireUser(request), merchantId);
        return Result.ok();
    }

    @GetMapping("/check/{merchantId}")
    @Operation(summary = "检查是否已关注", description = "检查当前用户是否已关注指定店铺;需登录")
    public Result<Boolean> check(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long merchantId) {
        return Result.ok(shopFollowService.isFollowed(AuthContext.requireUser(request), merchantId));
    }
}
