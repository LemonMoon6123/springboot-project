package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppShowService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/show")
public class AppShowController {

    @Resource
    private AppShowService appShowService;

    @GetMapping("/home")
    public Result home(@RequestParam(required = false) String city,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        return Result.ok(appShowService.home(city, type, current, size));
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String city,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String date,
                       @RequestParam(required = false, defaultValue = "time") String sort,
                       @RequestParam(required = false) Long venueId,
                       @RequestParam(required = false) Long artistId,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return Result.ok(appShowService.list(city, type, date, sort, venueId, artistId, current, size, userId));
    }

    @GetMapping("/{performanceId}")
    public Result detail(@PathVariable String performanceId,
                         @RequestParam(required = false) String showId,
                         @RequestParam(required = false, defaultValue = "false") boolean noticesAll) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        Long pid = parsePositiveLong(performanceId, "演出");
        Long sid = showId == null || showId.isEmpty() ? null : parsePositiveLong(showId, "场次");
        return Result.ok(appShowService.detail(pid, sid, noticesAll, userId));
    }

    private static Long parsePositiveLong(String raw, String label) {
        if (raw == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
        String s = raw.trim();
        if (s.isEmpty() || "undefined".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
    }
}