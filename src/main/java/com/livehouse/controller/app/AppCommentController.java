package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/show")
public class AppCommentController {

    @Resource
    private AppCommentService appCommentService;

    @GetMapping("/{performanceId}/comments")
    public Result list(@PathVariable Long performanceId,
                       @RequestParam(defaultValue = "all") String filter,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        return Result.ok(appCommentService.listComments(performanceId, filter, current, size));
    }

    @PostMapping("/{performanceId}/comment")
    public Result post(@PathVariable Long performanceId, @RequestBody Map<String, Object> body) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = Long.parseLong(String.valueOf(body.get("orderId")));
        Integer rating = body.get("rating") == null ? 5 : ((Number) body.get("rating")).intValue();
        String content = String.valueOf(body.get("content"));
        String images = body.get("images") == null ? null : String.valueOf(body.get("images"));
        appCommentService.postComment(userId, performanceId, orderId, rating, content, images);
        return Result.ok();
    }
}