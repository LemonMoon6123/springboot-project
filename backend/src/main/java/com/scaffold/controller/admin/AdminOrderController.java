package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.ShopOrder;
import com.scaffold.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders")
@Tag(name = "A13-管理端-订单管理", description = "管理员查看全平台订单")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    @Operation(summary = "订单分页查询", description = "按用户/商家/状态/关键词分页查询全平台订单;需管理员")
    public Result<PageResult<ShopOrder>> page(@Parameter(hidden = true) HttpServletRequest request,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) Long merchantId,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "") String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(orderService.pageForAdmin(userId, merchantId, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情", description = "获取指定订单详情;需管理员")
    public Result<ShopOrder> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(orderService.getDetail(id));
    }
}
