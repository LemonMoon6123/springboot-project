package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppVenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/venue")
public class AppVenueController {

    @Resource
    private AppVenueService appVenueService;

    @GetMapping("/{venueId}/location")
    public Result location(@PathVariable Long venueId) {
        return Result.ok(appVenueService.location(venueId));
    }
}