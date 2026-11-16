package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppVerifyService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/verify")
public class AppVerifyController {

    @Resource
    private AppVerifyService appVerifyService;

    @GetMapping("/sessions")
    public Result sessions() {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appVerifyService.sessions(UserHolder.getUser().getId()));
    }

    @PostMapping("/scan")
    public Result scan(@RequestBody Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        Long showId = Long.parseLong(String.valueOf(body.get("showId")));
        String ticketCode = body.get("ticketCode") == null ? null : body.get("ticketCode").toString();
        String shortCode = body.get("shortCode") == null ? null : body.get("shortCode").toString();
        String gate = body.get("gate") == null ? null : body.get("gate").toString();
        return Result.ok(appVerifyService.scan(UserHolder.getUser().getId(), showId, ticketCode, shortCode, gate));
    }

    @GetMapping("/stats/{showId}")
    public Result stats(@PathVariable Long showId) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appVerifyService.stats(UserHolder.getUser().getId(), showId));
    }
}