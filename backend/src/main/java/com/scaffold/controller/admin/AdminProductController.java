package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.Product;
import com.scaffold.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products")
@Tag(name = "A11-管理端-商品管理", description = "管理员商品 CRUD、上下架,可跨商家管理")
public class AdminProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    @Operation(summary = "商品分页查询", description = "按关键词/类目/商家/状态分页查询全平台商品;需管理员")
    public Result<PageResult<Product>> page(@Parameter(hidden = true) HttpServletRequest request,
                                            @RequestParam(defaultValue = "") String keyword,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long merchantId,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(productService.page(keyword, categoryId, merchantId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情", description = "获取指定商品详情;需管理员")
    public Result<Product> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(productService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建商品", description = "管理员代商家创建商品,需指定 merchantId;需管理员")
    public Result<Product> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Product product) {
        AuthContext.requireAdmin(request);
        Long merchantId = product.getMerchantId();
        if (merchantId == null) {
            return Result.fail("商家ID不能为空");
        }
        return Result.ok(productService.create(product, merchantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改商品", description = "修改指定商品信息(跨商家);需管理员")
    public Result<Product> update(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                  @RequestBody Product product) {
        AuthContext.requireAdmin(request);
        return Result.ok(productService.update(id, product, null, true));
    }

    @PostMapping("/{id}/on-shelf")
    @Operation(summary = "商品上架", description = "将指定商品上架(跨商家);需管理员")
    public Result<Product> onShelf(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(productService.onShelf(id, null, true));
    }

    @PostMapping("/{id}/off-shelf")
    @Operation(summary = "商品下架", description = "将指定商品下架(跨商家);需管理员")
    public Result<Product> offShelf(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(productService.offShelf(id, null, true));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "删除指定商品(跨商家);需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        productService.delete(id, null, true);
        return Result.ok();
    }
}
