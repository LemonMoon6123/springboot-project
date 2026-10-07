package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.Category;
import com.scaffold.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/categories")
@Tag(name = "A12-管理端-商品类目", description = "管理端商品类目 CRUD")
public class AdminCategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    @Operation(summary = "类目分页查询", description = "按关键词/状态分页查询类目;需管理员")
    public Result<PageResult<Category>> page(@Parameter(hidden = true) HttpServletRequest request,
                                             @RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(categoryService.page(keyword, status, page, size));
    }

    @PostMapping
    @Operation(summary = "创建类目", description = "新增商品类目;需管理员")
    public Result<Category> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Category category) {
        AuthContext.requireAdmin(request);
        return Result.ok(categoryService.create(category));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改类目", description = "修改指定类目信息;需管理员")
    public Result<Category> update(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                   @RequestBody Category category) {
        AuthContext.requireAdmin(request);
        return Result.ok(categoryService.update(id, category));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除类目", description = "删除指定类目;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        categoryService.delete(id);
        return Result.ok();
    }
}
