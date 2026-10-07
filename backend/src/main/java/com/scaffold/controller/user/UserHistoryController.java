package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.entity.BrowseHistory;
import com.scaffold.service.BrowseHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/history")
@Tag(name = "U08-用户端-浏览足迹", description = "浏览历史分页、记录、删除、清空")
public class UserHistoryController {

    @Autowired
    private BrowseHistoryService browseHistoryService;

    @GetMapping
    @Operation(summary = "足迹分页", description = "分页查询当前用户浏览足迹;需登录")
    public Result<PageResult<BrowseHistory>> page(@Parameter(hidden = true) HttpServletRequest request,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(browseHistoryService.page(AuthContext.requireUser(request), page, size));
    }

    @PostMapping
    @Operation(summary = "记录足迹", description = "记录指定商品的浏览足迹;需登录")
    public Result<BrowseHistory> record(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") == null ? null : Long.valueOf(body.get("productId").toString());
        return Result.ok(browseHistoryService.record(AuthContext.requireUser(request), productId));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "删除足迹", description = "删除指定商品的浏览足迹;需登录")
    public Result<Void> remove(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long productId) {
        browseHistoryService.remove(AuthContext.requireUser(request), productId);
        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "清空足迹", description = "清空当前用户全部浏览足迹;需登录")
    public Result<Void> clear(@Parameter(hidden = true) HttpServletRequest request) {
        browseHistoryService.clear(AuthContext.requireUser(request));
        return Result.ok();
    }
}
