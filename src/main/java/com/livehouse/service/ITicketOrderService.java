package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.Result;
import com.livehouse.entity.TicketOrder;

/**
 * 票务订单服务接口
 */
public interface ITicketOrderService extends IService<TicketOrder> {

    /**
     * 模拟支付回调
     * @param orderId 订单ID
     * @return 支付结果
     */
    Result simulatePayment(Long orderId);
}