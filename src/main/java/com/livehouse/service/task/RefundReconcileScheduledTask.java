package com.livehouse.service.task;

import com.livehouse.dto.RefundMessage;
import com.livehouse.entity.RefundRecord;
import com.livehouse.service.IMessageProducerService;
import com.livehouse.service.IRefundRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 退票记录对账/补发定时任务（本地消息表模式的"消息中继"）。
 *
 * 退票申请成功落库后（状态=处理中），会在DB事务提交后异步发送一条MQ消息触发库存回补；
 * 但消息发送本身不保证一定成功（网络抖动、Broker短暂不可用等），一旦丢失，
 * 单靠"发一次"是不够的。这个定时任务定期扫描：
 *   - 状态仍为"处理中"但创建超过5分钟（大概率消息丢失或还没被消费）
 *   - 状态为"处理失败"（消费者处理时抛出异常）
 * 的记录，重新发送一次退票消息，触发消费者重新尝试。
 *
 * 之所以不给退票队列配死信队列，是因为这张退票记录表本身就是可靠的状态存储——
 * 记录不会丢，只会停留在"处理中/失败"状态等待被这里重新触发，效果等价于死信队列，
 * 但实现更简单，也顺带获得了"退票处理历史可查询、可审计"的额外收益。
 */
@Slf4j
@Component
public class RefundReconcileScheduledTask {

    @Autowired
    private IRefundRecordService refundRecordService;

    @Autowired
    private IMessageProducerService messageProducerService;

    /**
     * 每5分钟扫描一次未完结的退票记录
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void resendPendingRefunds() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        List<RefundRecord> stuckRecords = refundRecordService.lambdaQuery()
                .in(RefundRecord::getStatus, Arrays.asList(RefundRecord.STATUS_PENDING, RefundRecord.STATUS_FAILED))
                .lt(RefundRecord::getCreateTime, threshold)
                .list();

        if (stuckRecords.isEmpty()) {
            return;
        }

        log.info("退票对账任务发现{}条未完结记录，重新发送退票消息", stuckRecords.size());

        for (RefundRecord record : stuckRecords) {
            try {
                RefundMessage message = new RefundMessage(
                        record.getId(), record.getOrderId(), record.getUserId(),
                        record.getTicketTypeId(), record.getQuantity());
                messageProducerService.sendRefundMessage(message);
            } catch (Exception e) {
                log.error("重新发送退票消息失败，退票记录ID：{}", record.getId(), e);
            }
        }
    }
}
