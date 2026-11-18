package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/message")
public class AppMessageController {

    @Resource
    private AppMessageService appMessageService;

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "20") int size) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(appMessageService.list(userId, type, current, size));
    }

    @PostMapping("/read-all")
    public Result readAll() {
        Long userId = UserHolder.getUser().getId();
        appMessageService.readAll(userId);
        return Result.ok();
    }
}