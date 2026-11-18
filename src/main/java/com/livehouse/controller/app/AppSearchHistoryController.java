package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppSearchHistoryService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/app/search/history")
public class AppSearchHistoryController {

    @Resource
    private AppSearchHistoryService appSearchHistoryService;

    @GetMapping
    public Result list() {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", appSearchHistoryService.list(UserHolder.getUser().getId()));
        return Result.ok(data);
    }

    @PostMapping
    public Result add(@RequestBody Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        String keyword = body.get("keyword") == null ? null : body.get("keyword").toString();
        return Result.ok(appSearchHistoryService.add(UserHolder.getUser().getId(), keyword));
    }

    @DeleteMapping
    public Result clear() {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        appSearchHistoryService.clear(UserHolder.getUser().getId());
        return Result.ok();
    }

    @DeleteMapping("/one")
    public Result removeOne(@RequestParam String keyword) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        appSearchHistoryService.removeOne(UserHolder.getUser().getId(), keyword);
        return Result.ok();
    }
}