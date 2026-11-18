package com.livehouse.controller.app;

import com.livehouse.dto.UserDTO;
import com.livehouse.entity.Result;
import com.livehouse.service.app.AppPointsService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/points")
public class AppPointsController {

    @Resource
    private AppPointsService appPointsService;

    @GetMapping("/shop")
    public Result shop() {
        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        return Result.ok(appPointsService.shop(userId));
    }

    @GetMapping("/logs")
    public Result logs(@RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "20") int size) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(appPointsService.logs(userId, current, size));
    }

    @PostMapping("/exchange")
    public Result exchange(@RequestBody Map<String, String> body) {
        Long userId = UserHolder.getUser().getId();
        Long goodsId = Long.parseLong(body.get("goodsId"));
        String idempotentKey = body.get("idempotentKey");
        return Result.ok(appPointsService.exchange(userId, goodsId, idempotentKey));
    }
}