package com.livehouse.service.consumer;

import com.livehouse.config.RabbitMQConfig;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.dto.SeckillOrderMessage;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.exception.DbStockInsufficientException;
import com.livehouse.service.IMessageProducerService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.RedisIDGenerator;
import com.livehouse.utils.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 秒杀订单消费者
 *
 * 库存恢复策略（靠路径互斥保证「只处理一次」，不为回补动作单独做幂等）：
 * 1. requestId 查库：防 broker 重复投递导致重复建单；
 * 可能的场景：
 * 处理完、ack 发出前崩溃 来不及发 网络抖动 ack 丢失 发了但 broker 没收到 重试期间消费者重启 retry 是本地循环，期间根本不会发 ack
 * 本质上都是broker没有收到至少一次的ack/nack导致的消息重复投递
 *
 * 解释：防 broker 的 at-least-once 投递语义 带来的重复投递——broker 为了不丢消息，宁可多送几次，幂等就在消费者端把重复的挡掉。
 *
 * RabbitMQ 的投递语义是 at-least-once（至少一次） ：只要 broker 没收到对这条消息的 ack/nack，它就认为消费者没成功处理，必须重新投递，宁可重复不可丢失。
 *
 *
 * Spring AMQP 在消费者抛异常时自动发 nack，requeue 值取自`defaultRequeueRejected` 配置：
 *
 * - 没配 /`true（默认）` →`basicNack(requeue=true)` → 回原队列
 * - `false` →`basicNack(requeue=false)` → 走死信
 * 所以 broker 本身没有"丢弃"这个选项，它只是个执行者——requeue=true 它放回队列，requeue=false 它查队列有没有配 DLX，配了就转死信，没配才真正丢弃。
 *
 * 项目配了 DLX，所以只要把`default-requeue-rejected: false` 加上，重试耗尽的消息就会走死信 →`SeckillOrderDlxConsumer` 收到做库存兜底。
 *
 * 2. DB 库存不足：校准 Redis + 回补限购，回滚事务后确认消息，不重试、不进死信；
 * 3. 瞬时异常：不回补，抛出重试；耗尽后由死信消费者校准。
 *
 *
 * 整个执行流程：
 * 我再从头捋一遍，首先执行消费者方法，如果顺利执行完没有抛出异常，那么此时交由spring容器向broker发送ack（acknowledge-mode: auto），如果broker接收到，那这个消息就代表被正常消费了，这是这个项目目前的正常流程;
 * 如果消费者正常消费了消息，但是交由spring容器向broker发送ack的时候这个ack丢失了，或者因为网络问题反正就是这个数据包发送失败，那么它会检测时间是否超时，或者是框架对broker建立的tcp连接通信断了，那么broker强制执行重新投递入队。
 * 如果消费者在消费的过程中出现意外，或者再直白一点，就是这个消费方法执行抛异常了，但是这里具体会是(requeue = false)，
 * broker不会重新投递，但是又因为设置了retry,所以此时broker什么都不会干 ，并且消费者这时会不断重试直到次数耗尽，
 * 期间框架也不会向broker发送任何nack，直到最终如果消费成功了，那么会由框架向broker发送一次ack，但是如果失败了就会发送nack，之后我不是配置了死信队列了么，那么这个消息在nack的时候会被broker路由到死信队列中而不是被丢弃。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private IMessageProducerService messageProducerService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillScriptExecutor seckillScriptExecutor;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    @Transactional
    public void handleSeckillOrder(SeckillOrderMessage message) {

        // 幂等优化处理 因为生产者可能因网络问题发重多次相同的消息
        /**
         * 原因：AMQP 监听器被配成了多线程并发，不再是单消费者串行。在application.yml中
         *
         * 优化点：重复投递时少走「异常 + 重试 + 死信」这条贵路径，避免无意义重试打满死信
         */
        String requestId = message.getRequestId();
        log.info("收到秒杀订单消息，requestId：{}，用户ID：{}，票种ID：{}", requestId, message.getUserId(), message.getTicketTypeId());

        if (requestId != null) {
            TicketOrder existing = ticketOrderService.lambdaQuery()
                    .eq(TicketOrder::getRequestId, requestId)
                    .one();
            if (existing != null) {
                log.info("检测到重复消息（订单已存在，幂等跳过），requestId：{}，订单ID：{}", requestId, existing.getId());
                return;
            }
        }

        // （Main Task）创建订单，扣减库存
        try {
            Long orderId = redisIDGenerator.getId("ticket_order");

            TicketOrder order = new TicketOrder();
            order.setId(orderId);
            order.setUserId(message.getUserId());
            order.setShowId(message.getShowId());
            order.setTicketTypeId(message.getTicketTypeId());
            order.setQuantity(message.getQuantity());
            order.setAmount(message.getAmount());
            order.setPayStatus(0);
            order.setOrderStatus(1);
            order.setRequestId(requestId);

            ticketOrderService.save(order); // 订单入库

            boolean success = ticketTypeService.update() // 扣减库存
                    .setSql("left_stock = left_stock - " + message.getQuantity())
                    .eq("id", message.getTicketTypeId())
                    .gt("left_stock", message.getQuantity() - 1)
                    .update();

            if (!success) {
                throw new DbStockInsufficientException("数据库库存不足，订单创建失败");
            }

            // 成功创建订单并扣减库存，同时把这个消息放到延时队列里，这个队列里的信息将在15分钟后被broker投递到死信队列中，用于超时订单处理。
            OrderTimeoutMessage timeoutMessage = new OrderTimeoutMessage(
                    orderId,
                    message.getUserId(),
                    message.getTicketTypeId(),
                    message.getQuantity()
            );
            messageProducerService.sendOrderTimeoutMessage(timeoutMessage);

            log.info("订单创建成功，订单ID：{}，requestId：{}，用户ID：{}，票种ID：{}，数量：{}", orderId, requestId, message.getUserId(), message.getTicketTypeId(), message.getQuantity());

        } catch (DbStockInsufficientException e) {
            // 确定性失败：校准 Redis → 回补限购计数 → 回滚建单事务 → 确认消息（不重试）
            log.error("数据库库存不足，开始校准Redis库存，requestId：{}，用户ID：{}，票种ID：{}", requestId, message.getUserId(), message.getTicketTypeId(), e);
            reconcileRedisWithDbAndReleaseUserQuota(message);
            clearUserPendingStatus(message);

            // 标记事务回滚，避免 save(order) 被提交；同时不向外抛异常，避免框架重试。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        // 秒杀消息消费 + 自动重试 路线
        } catch (Exception e) {
            // 瞬时失败：不回补 Redis，保持 Lua 已扣状态，交给框架重试；
            // 若在这里回滚redis库存，因为在配置文件设置了消息重试次数，所以重试成功时会出现「MySQL 扣了、Redis 已还」的新漂移。
            log.error("处理秒杀订单消息失败（将重试），requestId：{}，用户ID：{}，票种ID：{}", requestId, message.getUserId(), message.getTicketTypeId(), e);
            throw new RuntimeException("订单创建失败", e);
        }
    }

    /**
     * 以 MySQL left_stock 覆盖 Redis 库存，同步mysql与 redis的库存数，不能直接回滚redis的库存。同时回补本次 Lua 占用的用户限购计数。
     */
    private void reconcileRedisWithDbAndReleaseUserQuota(SeckillOrderMessage message) {
        TicketType ticketType = ticketTypeService.getById(message.getTicketTypeId());
        Integer dbLeftStock = ticketType != null ? ticketType.getLeftStock() : 0;
        seckillScriptExecutor.syncRedisStockFromDb(message.getTicketTypeId(), dbLeftStock);

        seckillScriptExecutor.restoreUserBuyCount(
                message.getTicketTypeId(),
                message.getUserId(),
                message.getQuantity());

        log.warn("DB库存不足已处理：Redis已校准为{}，用户限购计数已回补，requestId：{}，票种ID：{}", dbLeftStock, message.getRequestId(), message.getTicketTypeId());
    }

    private void clearUserPendingStatus(SeckillOrderMessage message) {
        String userOrderStatusKey = "user:order:status:" +
                message.getUserId() + ":" + message.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);
    }
}
