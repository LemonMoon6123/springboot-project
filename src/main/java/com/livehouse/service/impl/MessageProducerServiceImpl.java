package com.livehouse.service.impl;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.dto.SeckillOrderMessage;
import com.livehouse.service.IMessageProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息生产者服务实现类
 */
@Slf4j
@Service
public class MessageProducerServiceImpl implements IMessageProducerService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public void sendSeckillOrderMessage(SeckillOrderMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
            );
            log.info("发送秒杀订单消息成功，用户ID：{}，票种ID：{}", 
                    message.getUserId(), message.getTicketTypeId());
        } catch (Exception e) {
            log.error("发送秒杀订单消息失败，用户ID：{}，票种ID：{}", 
                    message.getUserId(), message.getTicketTypeId(), e);
            // 重新抛出异常，让上层知道发送失败
            throw new RuntimeException("消息发送失败", e);
        }
    }

    @Override
    public void sendOrderTimeoutMessage(OrderTimeoutMessage message) {
        try {
            // 发送到延迟队列，15分钟后自动进入死信队列
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.DELAY_QUEUE,
                message
            );
            log.info("发送订单超时延迟消息成功，订单ID：{}", message.getOrderId());
        } catch (Exception e) {
            log.error("发送订单超时延迟消息失败，订单ID：{}", message.getOrderId(), e);
            // 重新抛出异常，让上层知道发送失败
            throw new RuntimeException("超时消息发送失败", e);
        }
    }
}