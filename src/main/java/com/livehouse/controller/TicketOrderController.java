package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.dto.UserDTO;
import com.livehouse.entity.TicketOrder;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 票务订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/order")
public class TicketOrderController {

    @Autowired
    private ITicketOrderService ticketOrderService;

    /**
     * 查询用户订单列表
     */
    @GetMapping("/list")
    public Result queryUserOrders() {
        UserDTO user = UserHolder.getUser();
        List<TicketOrder> orders = ticketOrderService.lambdaQuery()
                .eq(TicketOrder::getUserId, user.getId())
                .orderByDesc(TicketOrder::getCreateTime)
                .list();

        return Result.ok(orders);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    public Result queryOrderById(@PathVariable Long orderId) {
        UserDTO user = UserHolder.getUser();
        TicketOrder order = ticketOrderService.lambdaQuery()
                .eq(TicketOrder::getId, orderId)
                .eq(TicketOrder::getUserId, user.getId())
                .one();

        if (order == null) {
            return Result.fail("订单不存在");
        }

        return Result.ok(order);
    }

    /**
     * 模拟支付订单
     */
    @PostMapping("/pay/{orderId}")
    public Result payOrder(@PathVariable Long orderId) {
        UserDTO user = UserHolder.getUser();
        // 验证订单归属
        TicketOrder order = ticketOrderService.lambdaQuery()
                .eq(TicketOrder::getId, orderId)
                .eq(TicketOrder::getUserId, user.getId())
                .one();

        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 调用模拟支付服务
        return ticketOrderService.simulatePayment(orderId);
    }
}