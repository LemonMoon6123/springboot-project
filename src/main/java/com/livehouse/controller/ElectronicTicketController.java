package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.dto.UserDTO;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 电子票控制器
 */
@Slf4j
@RestController
@RequestMapping("/ticket")
public class ElectronicTicketController {

    @Autowired
    private IElectronicTicketService electronicTicketService;

    @Autowired
    private ITicketOrderService ticketOrderService;

    /**
     * 模拟支付接口
     */
    @PostMapping("/simulate-payment/{orderId}")
    public Result simulatePayment(@PathVariable Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }

        return ticketOrderService.simulatePayment(orderId);
    }

    /**
     * 核销电子票
     */
    @PostMapping("/verify/{ticketCode}")
    public Result verifyTicket(@PathVariable String ticketCode) {
        log.info("核销电子票请求，票号：{}", ticketCode);
        return electronicTicketService.verifyTicket(ticketCode);
    }

    /**
     * 查询电子票详情
     */
    @GetMapping("/detail/{ticketCode}")
    public Result getTicketDetail(@PathVariable String ticketCode) {
        return electronicTicketService.getTicketByCode(ticketCode);
    }

    /**
     * 查询当前用户电子票
     */
    @GetMapping("/my")
    public Result myTickets() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        return electronicTicketService.queryUserTickets(user.getId());
    }

    /**
     * 手动生成电子票（管理员功能）
     */
    @PostMapping("/generate/{orderId}")
    public Result generateTickets(@PathVariable Long orderId) {
        return electronicTicketService.generateElectronicTickets(orderId);
    }
}