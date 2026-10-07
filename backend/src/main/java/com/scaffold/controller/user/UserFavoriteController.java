package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.entity.ProductFavorite;
import com.scaffold.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/favorites")
@Tag(name = "U06-用户端-商品收藏", description = "收藏列表、收藏/取消收藏、收藏状态检查")
public class UserFavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    @Operation(summary = "收藏列表", description = "查询当前用户收藏的商品;需登录")
    public Result<List<ProductFavorite>> list(@Parameter(hidden = true) HttpServletRequest request) {
        return Result.ok(favoriteService.listByUser(AuthContext.requireUser(request)));
    }

    @PostMapping
    @Operation(summary = "收藏商品", description = "收藏指定商品;需登录")
    public Result<ProductFavorite> add(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") == null ? null : Long.valueOf(body.get("productId").toString());
        return Result.ok(favoriteService.add(AuthContext.requireUser(request), productId));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "取消收藏", description = "取消收藏指定商品;需登录")
    public Result<Void> remove(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long productId) {
        favoriteService.remove(AuthContext.requireUser(request), productId);
        return Result.ok();
    }

    @GetMapping("/check/{productId}")
    @Operation(summary = "检查是否已收藏", description = "检查当前用户是否已收藏指定商品;需登录")
    public Result<Boolean> check(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long productId) {
        return Result.ok(favoriteService.isFavorite(AuthContext.requireUser(request), productId));
    }
}
