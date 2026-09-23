package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.SeckillOrderMessage;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单死信队列兜底消费者。
 *
 * 只处理「瞬时异常重试耗尽」的消息；DB 库存不足在主消费者已确认消息，不会进入这里。
 * 因此与主消费者的限购回补路径互斥，各执行一次即可，无需再套幂等标记。
 */
@Slf4j
@Component
public class SeckillOrderDlxConsumer {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_DLX_QUEUE)
    public void handleSeckillOrderDlx(SeckillOrderMessage message) {
        String requestId = message.getRequestId();
        log.error("秒杀订单消费重试耗尽，进入死信队列，开始兜底处理，requestId：{}，用户ID：{}，票种ID：{}，数量：{}", requestId, message.getUserId(), message.getTicketTypeId(), message.getQuantity());

        if (requestId != null) {
            TicketOrder existing = ticketOrderService.lambdaQuery()
                    .eq(TicketOrder::getRequestId, requestId)
                    .one();
            if (existing != null) {
                log.info("死信兜底时发现订单已存在，无需释放库存，requestId：{}，订单ID：{}", requestId, existing.getId());
                return;
            }
        }

        TicketType ticketType = ticketTypeService.getById(message.getTicketTypeId());
        Integer dbLeftStock = ticketType != null ? ticketType.getLeftStock() : 0;

        // 对账数据库，回滚redis相应缓存数据
        seckillScriptExecutor.syncRedisStockFromDb(message.getTicketTypeId(), dbLeftStock);
        seckillScriptExecutor.restoreUserBuyCount(message.getTicketTypeId(), message.getUserId(), message.getQuantity());

        String userOrderStatusKey = "user:order:status:" + message.getUserId() + ":" + message.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);

        log.error("死信兜底完成：Redis已校准为DB库存{}，requestId：{}，用户ID：{}，票种ID：{}", dbLeftStock, requestId, message.getUserId(), message.getTicketTypeId());
    }
}
