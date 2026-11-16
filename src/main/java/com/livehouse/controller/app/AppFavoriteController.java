package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppFavoriteService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/favorite")
public class AppFavoriteController {

    @Resource
    private AppFavoriteService appFavoriteService;

    @PostMapping
    public Result add(@RequestBody Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        String targetType = body.get("targetType") == null ? null : body.get("targetType").toString();
        Long targetId = parseLong(body.get("targetId"));
        return Result.ok(appFavoriteService.add(UserHolder.getUser().getId(), targetType, targetId));
    }

    @DeleteMapping
    public Result remove(@RequestBody Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        String targetType = body.get("targetType") == null ? null : body.get("targetType").toString();
        Long targetId = parseLong(body.get("targetId"));
        return Result.ok(appFavoriteService.remove(UserHolder.getUser().getId(), targetType, targetId));
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appFavoriteService.list(UserHolder.getUser().getId(), type, current, size));
    }

    private static Long parseLong(Object v) {
        if (v == null) {
            return null;
        }
        return Long.parseLong(v.toString());
    }
}