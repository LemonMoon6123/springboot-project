package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.dto.app.PayPrepayRequest;
import com.livehouse.service.app.AppPayService;
import com.livehouse.vo.app.pay.WxNotifyResponseVo;
import com.livehouse.utils.UserHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/pay")
public class AppPayController {

    @Resource
    private AppPayService appPayService;

    @PostMapping("/prepay")
    public Result prepay(@RequestBody PayPrepayRequest body) {
        requireLogin();
        if (body == null || body.getOrderId() == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        String channel = body.getChannel() == null ? "wxpay" : body.getChannel();
        return Result.ok(appPayService.prepay(UserHolder.getUser().getId(), body.getOrderId(), channel));
    }

    @PostMapping("/notify/wxpay")
    public WxNotifyResponseVo wxNotify(@RequestBody(required = false) Map<String, Object> body) {
        return appPayService.handleWxNotify(body == null ? new java.util.HashMap<>() : body);
    }

    @Profile({"default", "dev"})
    @PostMapping("/mock/{orderId}")
    public Result mockPay(@PathVariable Long orderId) {
        requireLogin();
        appPayService.mockPay(UserHolder.getUser().getId(), orderId);
        return Result.ok();
    }

    private void requireLogin() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
    }

}