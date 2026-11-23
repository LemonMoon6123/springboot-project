package com.livehouse.controller.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livehouse.dto.app.CommentPostRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/app/show")
public class AppCommentController {

    @Resource
    private AppCommentService appCommentService;

    @GetMapping("/{showId}/comments")
    public Result list(@PathVariable String showId,
                       @RequestParam(defaultValue = "all") String filter,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return Result.ok(appCommentService.listComments(parseId(showId, "场次"), filter, current, size, userId));
    }

    @PostMapping("/{showId}/comment")
    public Result post(@PathVariable String showId, @RequestBody CommentPostRequest body) {
        Long userId = UserHolder.getUser().getId();
        if (body == null || body.getOrderId() == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "缺少订单号");
        }
        Integer rating = body.getRating() == null ? 5 : body.getRating();
        String content = body.getContent() == null ? "" : body.getContent();
        appCommentService.postComment(userId, parseId(showId, "场次"), body.getOrderId(), rating,
                content, toImagesJson(body.getImages()));
        return Result.ok();
    }

    private static Long parseId(String raw, String label) {
        if (raw == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
        String s = raw.trim();
        if (s.isEmpty() || "undefined".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, label + "ID无效");
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

  private static String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > 6) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "配图最多 6 张");
        }
        try {
            return JSON.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "配图格式错误");
        }
    }
}