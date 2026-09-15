package com.livehouse.service;

import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.dto.SeckillOrderMessage;

/**
 * 消息生产者服务接口
 */
public interface IMessageProducerService {

    /**
     * 发送秒杀订单消息
     * @param message 秒杀订单消息
     */
    void sendSeckillOrderMessage(SeckillOrderMessage message);

    /**
     * 发送订单超时延迟消息
     * @param message 订单超时消息
     */
    void sendOrderTimeoutMessage(OrderTimeoutMessage message);
}