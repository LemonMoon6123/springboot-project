package com.livehouse.service.app.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.entity.Payment;
import com.livehouse.entity.TicketOrder;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.mapper.PaymentMapper;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.app.AppPayService;
import com.livehouse.service.app.PaymentSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppPayServiceImpl implements AppPayService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ITicketOrderService ticketOrderService;
    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private PaymentSuccessHandler paymentSuccessHandler;
    @Resource
    private LivehouseProperties livehouseProperties;

    @Override
    @Transactional
    public Map<String, Object> prepay(Long userId, Long orderId, String channel) {
        TicketOrder order = ticketOrderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        if (order.getPayStatus() == 1) {
            throw new StatusCodeException(StatusCode.ORDER_ALREADY_PAID);
        }
        if (order.getOrderStatus() != 1) {
            throw new StatusCodeException(StatusCode.ORDER_STATUS_ILLEGAL);
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new StatusCodeException(StatusCode.ORDER_EXPIRED);
        }

        String ch = channel == null ? "wxpay" : channel;
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getChannel, ch));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setChannel(ch);
            payment.setAmount(order.getPayAmount());
            payment.setStatus(Payment.STATUS_PENDING);
            payment.setExpireTime(order.getExpireTime());
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.insert(payment);
        }

        Map<String, Object> payParams = new LinkedHashMap<>();
        if (livehouseProperties.getPay().isMockEnabled()) {
            payParams.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
            payParams.put("nonceStr", UUID.randomUUID().toString(true));
            payParams.put("package", "prepay_id=mock_" + order.getOrderNo());
            payParams.put("signType", "RSA");
            payParams.put("paySign", "mock_sign");
        } else {
            payParams.put("timeStamp", "");
            payParams.put("nonceStr", "");
            payParams.put("package", "");
            payParams.put("signType", "RSA");
            payParams.put("paySign", "");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payParams", payParams);
        data.put("paymentId", String.valueOf(payment.getId()));
        data.put("expireTime", order.getExpireTime() == null ? null : order.getExpireTime().format(DT));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> handleWxNotify(Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", "SUCCESS");
        resp.put("message", "成功");
        return resp;
    }

    @Override
    @Transactional
    public void mockPay(Long userId, Long orderId) {
        if (!livehouseProperties.getPay().isMockEnabled()) {
            throw new StatusCodeException(StatusCode.PAY_MOCK_DISABLED);
        }
        TicketOrder order = ticketOrderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        paymentSuccessHandler.handlePaymentSuccess(orderId);
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId).eq(Payment::getChannel, "wxpay").last("LIMIT 1"));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setChannel("wxpay");
            payment.setAmount(order.getPayAmount());
            payment.setStatus(Payment.STATUS_SUCCESS);
            payment.setNotifyTime(LocalDateTime.now());
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.insert(payment);
        } else if (payment.getStatus() != Payment.STATUS_SUCCESS) {
            payment.setStatus(Payment.STATUS_SUCCESS);
            payment.setNotifyTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }
    }
}