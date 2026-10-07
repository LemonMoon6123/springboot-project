package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.HomeBanner;
import com.scaffold.service.HomeBannerService;
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
@RequestMapping("/admin/banners")
@Tag(name = "A20-管理端-首页轮播", description = "管理员首页 Banner CRUD")
public class AdminBannerController {

    @Autowired
    private HomeBannerService homeBannerService;

    @GetMapping
    @Operation(summary = "Banner 分页查询", description = "按关键词分页查询首页 Banner;需管理员")
    public Result<PageResult<HomeBanner>> page(@Parameter(hidden = true) HttpServletRequest request,
                                               @RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(homeBannerService.page(keyword, page, size));
    }

    @PostMapping
    @Operation(summary = "创建 Banner", description = "新增首页 Banner;需管理员")
    public Result<HomeBanner> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody HomeBanner banner) {
        AuthContext.requireAdmin(request);
        return Result.ok(homeBannerService.create(banner));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改 Banner", description = "修改指定 Banner;需管理员")
    public Result<HomeBanner> update(@Parameter(hidden = true) HttpServletRequest request,
                                     @PathVariable Long id,
                                     @RequestBody HomeBanner banner) {
        AuthContext.requireAdmin(request);
        return Result.ok(homeBannerService.update(id, banner));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Banner", description = "删除指定 Banner;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        homeBannerService.delete(id);
        return Result.ok();
    }
}
