package com.scaffold.controller.user;

import com.scaffold.common.Result;
import com.scaffold.entity.Announcement;
import com.scaffold.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/announcements")
@Tag(name = "U09-用户端-系统公告", description = "查询已启用的系统公告列表与详情")
public class UserAnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @GetMapping
    @Operation(summary = "公告列表", description = "返回所有已启用的系统公告")
    public Result<List<Announcement>> list() {
        return Result.ok(announcementService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "公告详情", description = "根据 ID 获取公告详情;未启用返回 404")
    public Result<Announcement> detail(@PathVariable Long id) {
        Announcement a = announcementService.getById(id);
        if (a.getStatus() == null || a.getStatus() != 1) {
            return Result.fail(404, "公告不存在");
        }
        return Result.ok(a);
    }
}
