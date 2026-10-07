package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.entity.Venue;
import com.livehouse.service.IVenueService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 场馆控制器
 */
@RestController
@RequestMapping("/venue")
public class VenueController {

    @Resource
    private IVenueService venueService;

    /**
     * 根据ID查询场馆信息
     * @param id 场馆ID
     * @return 场馆详情
     */
    @GetMapping("/{id}")
    public Result queryVenueById(@PathVariable("id") Long id) {
        Venue venue = venueService.queryVenueById(id);
        if (venue == null) {
            return Result.fail("场馆不存在");
        }
        return Result.ok(venue);
    }

    /**
     * 根据城市查询场馆列表
     * @param city 城市名称
     * @return 场馆列表
     */
    @GetMapping("/of/city")
    public Result queryVenuesByCity(@RequestParam("city") String city) {
        return Result.ok(venueService.query().eq("city", city).list());
    }

    /**
     * 新增场馆
     * @param venue 场馆信息
     * @return 场馆ID
     */
    @PostMapping
    public Result saveVenue(@RequestBody Venue venue) {
        venueService.saveVenue(venue);
        return Result.ok(venue.getId());
    }

    /**
     * 更新场馆信息
     * @param venue 场馆信息
     * @return 更新结果
     */
    @PutMapping
    public Result updateVenue(@RequestBody Venue venue) {
        if (venue.getId() == null) {
            return Result.fail("场馆不存在");
        }
        venueService.updateVenue(venue);
        return Result.ok();
    }
}