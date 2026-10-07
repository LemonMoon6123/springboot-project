package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.Admin;
import com.scaffold.service.AdminService;
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
@RequestMapping("/admin/admins")
@Tag(name = "A22-管理端-管理员管理", description = "管理员账号 CRUD")
public class AdminAdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    @Operation(summary = "管理员分页查询", description = "按关键词分页查询管理员;需管理员")
    public Result<PageResult<Admin>> page(@Parameter(hidden = true) HttpServletRequest request,
                                          @RequestParam(defaultValue = "") String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(adminService.page(keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "管理员详情", description = "获取指定管理员详情;需管理员")
    public Result<Admin> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(adminService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建管理员", description = "新增管理员账号;需管理员")
    public Result<Admin> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Admin admin) {
        AuthContext.requireAdmin(request);
        return Result.ok(adminService.create(admin));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改管理员", description = "修改指定管理员信息;需管理员")
    public Result<Admin> update(@Parameter(hidden = true) HttpServletRequest request,
                                @PathVariable Long id,
                                @RequestBody Admin admin) {
        AuthContext.requireAdmin(request);
        return Result.ok(adminService.update(id, admin));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除管理员", description = "删除指定管理员;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        adminService.delete(id);
        return Result.ok();
    }
}
