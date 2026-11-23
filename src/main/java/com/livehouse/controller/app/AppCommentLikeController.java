package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/comment")
public class AppCommentLikeController {

    @Resource
    private AppCommentService appCommentService;

  /** 点赞/取消点赞 */
    @PostMapping("/{commentId}/like")
    public Result toggleLike(@PathVariable Long commentId) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(appCommentService.toggleCommentLike(userId, commentId));
    }
}