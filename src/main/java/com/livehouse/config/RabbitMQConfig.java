package com.livehouse.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 秒杀下单队列配置 ====================
    
    /**
     * 秒杀下单直连交换机
     */
    public static final String SECKILL_EXCHANGE = "ticket.direct.exchange";
    
    /**
     * 秒杀下单队列
     */
    public static final String SECKILL_QUEUE = "ticket.seckill.queue";
    
    /**
     * 秒杀下单路由键
     */
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    /**
     * 秒杀下单死信交换机：消费者重试耗尽（框架默认重试max-attempts次后拒绝且不重新入队）后，
     * 消息会被路由到这里，由专门的兜底消费者做最终的库存归还，避免消息被静默丢弃导致
     * "Redis库存已扣但订单永远建不出来"的库存黑洞。
     */
    public static final String SECKILL_DLX_EXCHANGE = "ticket.seckill.dlx.exchange";

    /**
     * 秒杀下单死信队列
     */
    public static final String SECKILL_DLX_QUEUE = "ticket.seckill.dlx.queue";

    /**
     * 秒杀下单死信路由键
     */
    public static final String SECKILL_DLX_ROUTING_KEY = "dlx.seckill.order";

    @Bean("seckillExchange")
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean("seckillQueue")
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillBinding(@Qualifier("seckillQueue") Queue queue,
                                  @Qualifier("seckillExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(SECKILL_ROUTING_KEY);
    }

    @Bean("seckillDlxExchange")
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(SECKILL_DLX_EXCHANGE);
    }

    @Bean("seckillDlxQueue")
    public Queue seckillDlxQueue() {
        return QueueBuilder.durable(SECKILL_DLX_QUEUE).build();
    }

    @Bean
    public Binding seckillDlxBinding(@Qualifier("seckillDlxQueue") Queue queue,
                                     @Qualifier("seckillDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(SECKILL_DLX_ROUTING_KEY);
    }

    // ==================== 退票队列配置 ====================

    /**
     * 退票队列：订单退票申请落库（本地消息表）后异步发送，用于回补Redis/MySQL库存。
     * 复用秒杀直连交换机，通过不同的路由键区分。
     */
    public static final String REFUND_QUEUE = "ticket.refund.queue";

    /**
     * 退票路由键
     */
    public static final String REFUND_ROUTING_KEY = "refund.order";

    @Bean("refundQueue")
    public Queue refundQueue() {
        return QueueBuilder.durable(REFUND_QUEUE).build();
    }

    @Bean
    public Binding refundBinding(@Qualifier("refundQueue") Queue queue,
                                 @Qualifier("seckillExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(REFUND_ROUTING_KEY);
    }

    // ==================== 订单超时死信队列配置 ====================
    
    /**
     * 延迟队列
     */
    public static final String DELAY_QUEUE = "ticket.delay.queue";
    
    /**
     * 死信交换机
     */
    public static final String DLX_EXCHANGE = "ticket.dlx.exchange";
    
    /**
     * 死信队列
     */
    public static final String DLX_QUEUE = "ticket.dlx.queue";
    
    /**
     * 死信路由键
     */
    public static final String DLX_ROUTING_KEY = "dlx.order";

    /**
     * 延迟队列（15分钟TTL）
     */
    @Bean("delayQueue")
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-message-ttl", 15 * 60 * 1000) // 15分钟TTL
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE) // 死信交换机
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY) // 死信路由键
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean("dlxExchange")
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    /**
     * 死信队列
     */
    @Bean("dlxQueue")
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding dlxBinding(@Qualifier("dlxQueue") Queue queue,
                              @Qualifier("dlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DLX_ROUTING_KEY);
    }
}