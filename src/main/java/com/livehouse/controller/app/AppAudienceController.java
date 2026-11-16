package com.livehouse.controller.app;

import com.livehouse.dto.app.AudienceCreateRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppAudienceService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/audience")
public class AppAudienceController {

    @Resource
    private AppAudienceService appAudienceService;

    @GetMapping("/list")
    public Result list() {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appAudienceService.list(UserHolder.getUser().getId()));
    }

    @PostMapping
    public Result create(@RequestBody AudienceCreateRequest request) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appAudienceService.create(UserHolder.getUser().getId(), request));
    }
}