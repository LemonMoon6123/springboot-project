package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.User;
import com.scaffold.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@Tag(name = "A15-管理端-用户管理", description = "管理员用户 CRUD")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @Operation(summary = "用户分页查询", description = "按关键词/角色分页查询用户;需管理员")
    public Result<PageResult<User>> page(@Parameter(hidden = true) HttpServletRequest request,
                                         @RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(userService.page(keyword, role, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情", description = "获取指定用户详情;需管理员")
    public Result<User> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "管理员创建用户(可指定角色);需管理员")
    public Result<User> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody User user) {
        AuthContext.requireAdmin(request);
        return Result.ok(userService.create(user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改用户", description = "修改指定用户信息;需管理员")
    public Result<User> update(@Parameter(hidden = true) HttpServletRequest request,
                               @PathVariable Long id,
                               @RequestBody User user) {
        AuthContext.requireAdmin(request);
        return Result.ok(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "删除指定用户;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        userService.delete(id);
        return Result.ok();
    }
}
