package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppSearchService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/search")
public class AppSearchController {

    @Resource
    private AppSearchService appSearchService;

    @GetMapping
    public Result search(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String city,
                         @RequestParam(required = false) String time,
                         @RequestParam(required = false) Long artist,
                         @RequestParam(required = false) String price,
                         @RequestParam(required = false) Integer hasTicket,
                         @RequestParam(required = false) String sort,
                         @RequestParam(defaultValue = "1") int current,
                         @RequestParam(defaultValue = "10") int size) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return Result.ok(appSearchService.search(keyword, city, time, artist, price, hasTicket, sort, current, size, userId));
    }
}