package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.Announcement;
import com.scaffold.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/announcements")
@Tag(name = "A19-管理端-公告管理", description = "管理员公告 CRUD")
public class AdminAnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @GetMapping
    @Operation(summary = "公告分页查询", description = "按关键词/状态分页查询公告;需管理员")
    public Result<PageResult<Announcement>> page(@Parameter(hidden = true) HttpServletRequest request,
                                                 @RequestParam(defaultValue = "") String keyword,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(announcementService.page(keyword, status, page, size));
    }

    @PostMapping
    @Operation(summary = "创建公告", description = "新增公告;需管理员")
    public Result<Announcement> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Announcement announcement) {
        AuthContext.requireAdmin(request);
        return Result.ok(announcementService.create(announcement));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改公告", description = "修改指定公告;需管理员")
    public Result<Announcement> update(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                       @RequestBody Announcement announcement) {
        AuthContext.requireAdmin(request);
        return Result.ok(announcementService.update(id, announcement));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告", description = "删除指定公告;需管理员")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        announcementService.delete(id);
        return Result.ok();
    }
}
