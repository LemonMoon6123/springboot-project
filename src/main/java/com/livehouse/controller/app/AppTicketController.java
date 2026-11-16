package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppTicketService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/ticket")
public class AppTicketController {

    @Resource
    private AppTicketService appTicketService;

    @GetMapping("/wallet")
    public Result wallet(@RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "1") int current,
                         @RequestParam(defaultValue = "10") int size) {
        requireLogin();
        return Result.ok(appTicketService.wallet(UserHolder.getUser().getId(), status, current, size));
    }

    @PostMapping("/{ticketId}/present-code")
    public Result presentCode(@PathVariable Long ticketId) {
        requireLogin();
        return Result.ok(appTicketService.presentCode(UserHolder.getUser().getId(), ticketId));
    }

    private void requireLogin() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
    }
}