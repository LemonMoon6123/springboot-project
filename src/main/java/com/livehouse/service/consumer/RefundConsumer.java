package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.RefundMessage;
import com.livehouse.entity.RefundRecord;
import com.livehouse.service.IRefundRecordService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.RedisConstants;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 退票消息消费者：负责把退票记录（本地消息表）真正落地为库存回补。
 *
 * 幂等设计：
 * 1. 消费前先查退票记录，状态已经是"已完成"则直接跳过（记录本身就是幂等判断的依据，
 *    无论这条消息被消费多少次——正常消费、框架内部重试、定时任务重新发送——都天然收敛）；
 * 2. 真正的Redis库存归还额外用 {@link SeckillScriptExecutor#executeRestoreStockIdempotent}
 *    做二次兜底，避免"记录状态更新"和"Redis归还"两步之间万一出现中间态被重复执行。
 *
 * 这里没有像秒杀队列一样额外配一个死信队列：因为退票场景在发消息之前已经把退票记录
 * 落库为PENDING状态（本地消息表/事务性消息模式），消息即使彻底丢失，
 * {@link com.livehouse.service.task.RefundReconcileScheduledTask} 也会定时扫描出未完成的记录重新发送，
 * 数据库记录本身就是最终一致性的保障，不需要再依赖broker层的死信机制。
 */
@Slf4j
@Component
public class RefundConsumer {

    @Autowired
    private IRefundRecordService refundRecordService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.REFUND_QUEUE)
    @Transactional
    public void handleRefund(RefundMessage message) {
        log.info("收到退票消息，退票记录ID：{}，订单ID：{}", message.getRefundRecordId(), message.getOrderId());

        RefundRecord record = refundRecordService.getById(message.getRefundRecordId());
        if (record == null) {
            log.warn("退票记录不存在，忽略该消息，退票记录ID：{}", message.getRefundRecordId());
            return;
        }

        if (record.getStatus() != null && record.getStatus() == RefundRecord.STATUS_SUCCESS) {
            log.info("退票记录已处理完成，幂等跳过，退票记录ID：{}", record.getId());
            return;
        }

        try {
            // 1. Redis库存幂等归还（同一退票记录只会真正归还一次，与下面的DB回补各自独立幂等，
            //    避免"其中一步失败重试"时，已经成功的另一步被误判需要跳过或被重复执行）
            seckillScriptExecutor.executeRestoreStockIdempotent(
                    "refund-" + record.getId(),
                    RedisConstants.REFUND_RESTORED_KEY,
                    message.getTicketTypeId(),
                    message.getUserId(),
                    message.getQuantity());

            // 2. 数据库库存回补（票种表left_stock），单独用一个Redis标记做幂等，
            //    保证重试时"已成功的DB回补"不会被重复执行，而"之前失败的DB回补"仍会被正常重试
            String dbRestoredKey = RedisConstants.REFUND_RESTORED_KEY + "db:" + record.getId();
            Boolean dbFirstTime = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dbRestoredKey, "1", RedisConstants.SECKILL_IDEMPOTENT_TTL, TimeUnit.HOURS);
            if (Boolean.TRUE.equals(dbFirstTime)) {
                boolean dbOk = ticketTypeService.update()
                        .setSql("left_stock = left_stock + " + message.getQuantity())
                        .eq("id", message.getTicketTypeId())
                        .update();
                if (!dbOk) {
                    // 失败则释放标记，允许后续重试真正执行DB回补
                    stringRedisTemplate.delete(dbRestoredKey);
                    log.warn("数据库库存回补未生效（票种可能已被删除），退票记录ID：{}", record.getId());
                }
            } else {
                log.info("数据库库存已回补过，跳过重复回补，退票记录ID：{}", record.getId());
            }

            // 3. 更新退票记录为已完成
            record.setStatus(RefundRecord.STATUS_SUCCESS);
            record.setFinishTime(LocalDateTime.now());
            refundRecordService.updateById(record);

            log.info("退票库存回补完成，退票记录ID：{}，订单ID：{}", record.getId(), message.getOrderId());
        } catch (Exception e) {
            log.error("退票库存回补失败，退票记录ID：{}，订单ID：{}", record.getId(), message.getOrderId(), e);
            record.setStatus(RefundRecord.STATUS_FAILED);
            refundRecordService.updateById(record);
            // 不再往外抛异常触发框架重试：状态已经落库为FAILED，交由定时任务统一重新发送，
            // 避免这里的异常导致@Transactional连record.setStatus的更新一起回滚。
        }
    }
}
