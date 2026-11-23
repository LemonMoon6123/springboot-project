package com.livehouse.controller.app;

import com.livehouse.dto.app.VerifyScanRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppVerifyService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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
    public Result scan(@RequestBody VerifyScanRequest body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        if (body == null || body.getShowId() == null) {
            return Result.fail(StatusCode.PARAM_ERROR);
        }
        return Result.ok(appVerifyService.scan(UserHolder.getUser().getId(), body.getShowId(),
                body.getTicketCode(), body.getShortCode(), body.getGate()));
    }

    @GetMapping("/stats/{showId}")
    public Result stats(@PathVariable Long showId) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appVerifyService.stats(UserHolder.getUser().getId(), showId));
    }
}