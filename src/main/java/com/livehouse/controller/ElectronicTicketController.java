package com.livehouse.controller;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.dto.UserDTO;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.app.VenueStaffService;
import com.livehouse.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @Autowired
    private VenueStaffService venueStaffService;

    /**
     * 模拟支付接口
     */
    @PostMapping("/simulate-payment/{orderId}")
    public Result simulatePayment(@PathVariable Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }

        return ticketOrderService.simulatePayment(orderId);
    }

    /**
     * 核销电子票（推荐：通过请求体 JSON 传参，避免 Base64 密文中的 +、/、= 导致 URL 400 错误）
     */
    @PostMapping("/verify")
    public Result verifyTicket(@RequestBody(required = false) Map<String, String> body,
                               @RequestParam(value = "ticketCode", required = false) String ticketCodeParam) {
        String ticketCode = null;
        if (body != null && body.containsKey("ticketCode")) {
            ticketCode = body.get("ticketCode");
        } else if (ticketCodeParam != null) {
            ticketCode = ticketCodeParam;
        }

        if (ticketCode == null || ticketCode.trim().isEmpty()) {
            return Result.fail(StatusCode.TICKET_CODE_EMPTY);
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        if (venueStaffService.listVenueIdsForStaff(user.getId()).isEmpty()) {
            return Result.fail(StatusCode.VERIFY_NO_PERMISSION);
        }

        ticketCode = ticketCode.trim();
        log.info("核销电子票请求，票号：{}", ticketCode);
        return electronicTicketService.verifyTicket(ticketCode);
    }

    /**
     * 核销电子票（兼容原有 PathVariable 路径）
     */
    @PostMapping("/verify/{ticketCode}")
    public Result verifyTicketByPath(@PathVariable String ticketCode) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        if (venueStaffService.listVenueIdsForStaff(user.getId()).isEmpty()) {
            return Result.fail(StatusCode.VERIFY_NO_PERMISSION);
        }
        log.info("核销电子票请求（Path），票号：{}", ticketCode);
        return electronicTicketService.verifyTicket(ticketCode);
    }

    /**
     * 查询电子票详情（支持 Query 参数）
     */
    @GetMapping("/detail")
    public Result getTicketDetailByParam(@RequestParam(value = "ticketCode", required = false) String ticketCode) {
        if (ticketCode == null || ticketCode.trim().isEmpty()) {
            return Result.fail(StatusCode.TICKET_CODE_EMPTY);
        }
        return electronicTicketService.getTicketByCode(ticketCode.trim());
    }

    /**
     * 查询电子票详情（兼容原有 PathVariable 路径）
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
            return Result.fail(StatusCode.UNAUTHORIZED);
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