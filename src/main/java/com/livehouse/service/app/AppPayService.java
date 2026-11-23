package com.livehouse.service.app;

import com.livehouse.vo.app.pay.PayPrepayVo;
import com.livehouse.vo.app.pay.WxNotifyResponseVo;

import java.util.Map;

public interface AppPayService {

    PayPrepayVo prepay(Long userId, Long orderId, String channel);

    WxNotifyResponseVo handleWxNotify(Map<String, Object> body);

    void mockPay(Long userId, Long orderId);
}