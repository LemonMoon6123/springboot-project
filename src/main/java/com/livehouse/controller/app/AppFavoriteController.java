package com.livehouse.controller.app;

import com.livehouse.dto.app.FavoriteTargetRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppFavoriteService;
import com.livehouse.util.app.FavoriteParamsResolver;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/favorite")
public class AppFavoriteController {

    @Resource
    private AppFavoriteService appFavoriteService;

    @PostMapping
    public Result add(@RequestBody(required = false) FavoriteTargetRequest body,
                      @RequestParam(required = false) String targetType,
                      @RequestParam(required = false) String targetId) {
        return doAdd(body, targetType, targetId);
    }

    @PostMapping("/{targetType}/{targetId}")
    public Result addByPath(@PathVariable String targetType, @PathVariable String targetId) {
        return doAdd(null, targetType, targetId);
    }

    @DeleteMapping
    public Result remove(@RequestBody(required = false) FavoriteTargetRequest body,
                         @RequestParam(required = false) String targetType,
                         @RequestParam(required = false) String targetId) {
        return doRemove(body, targetType, targetId);
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public Result removeByPath(@PathVariable String targetType, @PathVariable String targetId) {
        return doRemove(null, targetType, targetId);
    }

    private Result doAdd(FavoriteTargetRequest body, String targetType, String targetId) {
        Long userId = requireUserId();
        FavoriteParamsResolver.Resolved p = FavoriteParamsResolver.resolve(body, targetType, targetId);
        return Result.ok(appFavoriteService.add(userId, p.targetType, p.targetId));
    }

    private Result doRemove(FavoriteTargetRequest body, String targetType, String targetId) {
        Long userId = requireUserId();
        FavoriteParamsResolver.Resolved p = FavoriteParamsResolver.resolve(body, targetType, targetId);
        return Result.ok(appFavoriteService.remove(userId, p.targetType, p.targetId));
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

    private static Long requireUserId() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED, "登录态无效，请重新登录");
        }
        return userId;
    }
}