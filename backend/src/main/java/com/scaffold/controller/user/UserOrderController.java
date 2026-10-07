package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.CreateOrderRequest;
import com.scaffold.entity.ShopOrder;
import com.scaffold.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/orders")
@Tag(name = "U03-用户端-订单", description = "下单、订单分页、详情、支付、取消、确认收货、售后申请")
public class UserOrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @Operation(summary = "创建订单", description = "根据收货地址与购物车项下单;需登录")
    public Result<List<ShopOrder>> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody CreateOrderRequest body) {
        return Result.ok(orderService.create(AuthContext.requireUser(request), body));
    }

    @GetMapping
    @Operation(summary = "订单分页", description = "按状态分页查询当前用户订单;需登录")
    public Result<PageResult<ShopOrder>> page(@Parameter(hidden = true) HttpServletRequest request,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageForUser(AuthContext.requireUser(request), status, page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "订单统计", description = "返回各状态订单数量;需登录")
    public Result<Map<String, Long>> stats(@Parameter(hidden = true) HttpServletRequest request) {
        return Result.ok(orderService.statsForUser(AuthContext.requireUser(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情", description = "查询当前用户指定订单详情;需登录")
    public Result<ShopOrder> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(orderService.getOwnedByUser(AuthContext.requireUser(request), id));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单", description = "对未支付订单发起支付;需登录")
    public Result<ShopOrder> pay(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(orderService.pay(AuthContext.requireUser(request), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "取消未支付订单;需登录")
    public Result<ShopOrder> cancel(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(orderService.cancel(AuthContext.requireUser(request), id));
    }

    @PostMapping("/{id}/after-sale")
    @Operation(summary = "申请售后", description = "对已支付/已发货订单申请退款/退货/换货;需登录")
    public Result<ShopOrder> applyAfterSale(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                            @RequestBody com.scaffold.dto.AfterSaleApplyRequest body) {
        return Result.ok(orderService.applyAfterSale(AuthContext.requireUser(request), id, body));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认收货", description = "确认收货,完成订单;需登录")
    public Result<ShopOrder> confirm(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(orderService.confirm(AuthContext.requireUser(request), id));
    }
}
