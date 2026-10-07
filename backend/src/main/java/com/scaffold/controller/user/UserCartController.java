package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.entity.CartItem;
import com.scaffold.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/cart")
@Tag(name = "U02-用户端-购物车", description = "购物车查询、加购、修改数量、删除、清空")
public class UserCartController {

    @Autowired
    private CartService cartService;

    @GetMapping
    @Operation(summary = "购物车列表", description = "查询当前用户购物车全部商品;需登录")
    public Result<List<CartItem>> list(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = AuthContext.requireUser(request);
        return Result.ok(cartService.list(userId));
    }

    @PostMapping
    @Operation(summary = "加入购物车", description = "指定商品与数量加入购物车;需登录")
    public Result<CartItem> add(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.requireUser(request);
        Long productId = body.get("productId") == null ? null : Long.valueOf(body.get("productId").toString());
        Integer quantity = body.get("quantity") == null ? 1 : Integer.valueOf(body.get("quantity").toString());
        return Result.ok(cartService.add(userId, productId, quantity));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改购物车数量", description = "修改指定购物车项的商品数量;需登录")
    public Result<CartItem> update(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.requireUser(request);
        Integer quantity = body.get("quantity") == null ? null : Integer.valueOf(body.get("quantity").toString());
        return Result.ok(cartService.updateQuantity(userId, id, quantity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除购物车项", description = "删除指定购物车项;需登录")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long userId = AuthContext.requireUser(request);
        cartService.delete(userId, id);
        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "清空购物车", description = "清空当前用户购物车;需登录")
    public Result<Void> clear(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = AuthContext.requireUser(request);
        cartService.clear(userId);
        return Result.ok();
    }
}
