package com.livehouse.service.app.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.entity.Payment;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.TicketOrder;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.PaymentMapper;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.app.AppPayService;
import com.livehouse.service.app.PaymentSuccessHandler;
import com.livehouse.vo.app.pay.PayPrepayVo;
import com.livehouse.vo.app.pay.WxNotifyResponseVo;
import com.livehouse.vo.app.pay.WxPayParamsVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class AppPayServiceImpl implements AppPayService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CHANNEL_WXPAY = "wxpay";

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
    public PayPrepayVo prepay(Long userId, Long orderId, String channel) {
        TicketOrder order = loadPendingOrder(userId, orderId);

        String ch = channel == null ? CHANNEL_WXPAY : channel.trim().toLowerCase();
        if (!CHANNEL_WXPAY.equals(ch)) {
            throw new StatusCodeException(StatusCode.PAY_FAILED, "仅支持微信支付");
        }
        Payment payment = ensurePendingPayment(userId, orderId, ch, order.getPayAmount(), order.getExpireTime());

        PayPrepayVo data = new PayPrepayVo();
        data.setChannel(CHANNEL_WXPAY);
        data.setPaymentId(String.valueOf(payment.getId()));
        data.setExpireTime(order.getExpireTime() == null ? null : order.getExpireTime().format(DT));

        WxPayParamsVo payParams = new WxPayParamsVo();
        if (livehouseProperties.getPay().isMockEnabled()) {
            payParams.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
            payParams.setNonceStr(UUID.randomUUID().toString(true));
            payParams.setPackageValue("prepay_id=mock_" + order.getOrderNo());
            payParams.setSignType("RSA");
            payParams.setPaySign("mock_sign");
        } else {
            payParams.setTimeStamp("");
            payParams.setNonceStr("");
            payParams.setPackageValue("");
            payParams.setSignType("RSA");
            payParams.setPaySign("");
        }
        data.setPayParams(payParams);
        return data;
    }

    @Override
    @Transactional
    public WxNotifyResponseVo handleWxNotify(Map<String, Object> body) {
        return WxNotifyResponseVo.success();
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
        markPaymentSuccess(orderId, userId, CHANNEL_WXPAY, "mock_" + order.getOrderNo(), null);
    }

    private void markPaymentSuccess(Long orderId, Long userId, String channel, String tradeNo, String rawNotify) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getChannel, channel)
                .last("LIMIT 1"));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setChannel(channel);
            payment.setAmount(ticketOrderService.getById(orderId).getPayAmount());
            payment.setStatus(Payment.STATUS_SUCCESS);
            payment.setTradeNo(tradeNo);
            payment.setNotifyTime(LocalDateTime.now());
            payment.setRawNotify(rawNotify);
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.insert(payment);
        } else if (payment.getStatus() != Payment.STATUS_SUCCESS) {
            payment.setStatus(Payment.STATUS_SUCCESS);
            payment.setTradeNo(tradeNo);
            payment.setNotifyTime(LocalDateTime.now());
            payment.setRawNotify(rawNotify);
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }
    }

    private TicketOrder loadPendingOrder(Long userId, Long orderId) {
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
        return order;
    }

    private Payment ensurePendingPayment(Long userId, Long orderId, String channel,
                                         BigDecimal amount, LocalDateTime expireTime) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getChannel, channel));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setChannel(channel);
            payment.setAmount(amount);
            payment.setStatus(Payment.STATUS_PENDING);
            payment.setExpireTime(expireTime);
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.insert(payment);
        }
        return payment;
    }
}