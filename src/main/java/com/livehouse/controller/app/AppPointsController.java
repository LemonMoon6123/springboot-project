package com.livehouse.controller.app;

import com.livehouse.dto.UserDTO;
import com.livehouse.dto.app.PointsExchangeRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppPointsService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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
    public Result exchange(@RequestBody PointsExchangeRequest body) {
        Long userId = UserHolder.getUser().getId();
        if (body == null || body.getGoodsId() == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        return Result.ok(appPointsService.exchange(userId, body.getGoodsId(), body.getIdempotentKey()));
    }
}