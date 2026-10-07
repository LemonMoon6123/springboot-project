package com.scaffold.controller.user;

import com.scaffold.common.Result;
import com.scaffold.entity.HomeBanner;
import com.scaffold.service.HomeBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/banners")
@Tag(name = "U10-用户端-首页轮播图", description = "首页 Banner 轮播图列表")
public class UserBannerController {

    @Autowired
    private HomeBannerService homeBannerService;

    @GetMapping
    @Operation(summary = "轮播图列表", description = "返回所有已启用的首页轮播图")
    public Result<List<HomeBanner>> list() {
        return Result.ok(homeBannerService.listEnabled());
    }
}
