package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppShowService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

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
        Map<String, Object> data = appShowService.home(city, type, current, size);
        return Result.ok(data);
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
        return Result.ok(appShowService.list(city, type, date, sort, venueId, artistId, current, size));
    }

    @GetMapping("/{performanceId}")
    public Result detail(@PathVariable Long performanceId,
                         @RequestParam(required = false) Long showId,
                         @RequestParam(required = false, defaultValue = "false") boolean noticesAll) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return Result.ok(appShowService.detail(performanceId, showId, noticesAll, userId));
    }
}