package com.livehouse.controller.app;

import com.livehouse.dto.app.OrderBatchDeleteRequest;
import com.livehouse.dto.app.OrderCreateRequest;
import com.livehouse.dto.app.OrderPreviewRequest;
import com.livehouse.dto.app.OrderReasonRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppOrderService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/app/order")
public class AppOrderController {

    @Resource
    private AppOrderService appOrderService;

    @PostMapping("/preview")
    public Result preview(@RequestBody OrderPreviewRequest request) {
        requireLogin();
        return Result.ok(appOrderService.preview(UserHolder.getUser().getId(), request));
    }

    @PostMapping("/create")
    public Result create(@RequestBody OrderCreateRequest request) {
        requireLogin();
        return Result.ok(appOrderService.create(UserHolder.getUser().getId(), request));
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String og,
                       @RequestParam(required = false) Integer entryStatus,
                       @RequestParam(defaultValue = "1") int current,
                       @RequestParam(defaultValue = "10") int size) {
        requireLogin();
        return Result.ok(appOrderService.list(UserHolder.getUser().getId(), og, entryStatus, current, size));
    }

    @GetMapping("/{orderId}")
    public Result detail(@PathVariable Long orderId) {
        requireLogin();
        return Result.ok(appOrderService.detail(UserHolder.getUser().getId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public Result cancel(@PathVariable Long orderId, @RequestBody(required = false) OrderReasonRequest body) {
        requireLogin();
        String reason = body == null ? null : body.getReason();
        appOrderService.cancel(UserHolder.getUser().getId(), orderId, reason);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    public Result batchDelete(@RequestBody OrderBatchDeleteRequest body) {
        requireLogin();
        List<Long> orderIds = body == null ? null : body.getOrderIds();
        appOrderService.batchDelete(UserHolder.getUser().getId(), orderIds);
        return Result.ok();
    }

    @PostMapping("/{orderId}/refund")
    public Result refund(@PathVariable Long orderId, @RequestBody(required = false) OrderReasonRequest body) {
        requireLogin();
        String reason = body == null ? null : body.getReason();
        return Result.ok(appOrderService.refund(UserHolder.getUser().getId(), orderId, reason));
    }

    private void requireLogin() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
    }
}