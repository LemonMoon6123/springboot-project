package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.AccountProfile;
import com.scaffold.dto.RechargeRequest;
import com.scaffold.entity.RechargeRecord;
import com.scaffold.service.AuthService;
import com.scaffold.service.RechargeRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "U05-用户端-个人中心", description = "个人资料、账户余额、充值与充值记录")
public class UserProfileController {

    @Autowired
    private AuthService authService;
    @Autowired
    private RechargeRecordService rechargeRecordService;

    @GetMapping("/profile")
    @Operation(summary = "我的资料", description = "获取当前登录用户资料与余额;需登录")
    public Result<AccountProfile> profile(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = AuthContext.requireUser(request);
        return Result.ok(authService.getCurrentUser(userId, "USER"));
    }

    @PostMapping("/recharge")
    @Operation(summary = "账户充值", description = "为当前用户账户充值,返回更新后资料;需登录")
    public Result<AccountProfile> recharge(@Parameter(hidden = true) HttpServletRequest request, @RequestBody RechargeRequest body) {
        Long userId = AuthContext.requireUser(request);
        return Result.ok(authService.recharge(userId, body.getAmount()));
    }

    @GetMapping("/recharge/records")
    @Operation(summary = "充值记录分页", description = "分页查询当前用户充值记录;需登录")
    public Result<PageResult<RechargeRecord>> rechargeRecords(@Parameter(hidden = true) HttpServletRequest request,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(rechargeRecordService.page(AuthContext.requireUser(request), page, size));
    }
}
