package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.SysMenu;
import com.scaffold.service.SysMenuService;
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

import java.util.List;

@RestController
@RequestMapping("/admin/menus")
@Tag(name = "A21-管理端-菜单管理", description = "管理员后台菜单 CRUD")
public class AdminMenuController {

    @Autowired
    private SysMenuService sysMenuService;

    @GetMapping
    @Operation(summary = "菜单分页查询", description = "分页查询后台菜单;需管理员")
    public Result<PageResult<SysMenu>> page(@Parameter(hidden = true) HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysMenuService.page(page, size));
    }

    @GetMapping({"/tree", "/all"})
    @Operation(summary = "菜单树/全部", description = "获取全部菜单(树形);需管理员")
    public Result<List<SysMenu>> tree(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysMenuService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "菜单详情", description = "获取指定菜单详情;需管理员")
    public Result<SysMenu> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysMenuService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建菜单", description = "新增后台菜单;需管理员")
    public Result<SysMenu> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody SysMenu menu) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysMenuService.create(menu));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改菜单", description = "修改指定菜单;需管理员")
    public Result<SysMenu> update(@Parameter(hidden = true) HttpServletRequest request,
                                   @PathVariable Long id,
                                   @RequestBody SysMenu menu) {
        AuthContext.requireAdmin(request);
        return Result.ok(sysMenuService.update(id, menu));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单", description = "删除指定菜单;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        sysMenuService.delete(id);
        return Result.ok();
    }
}
