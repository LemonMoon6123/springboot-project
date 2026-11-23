package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.RefundRecord;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketOrder;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.IRefundRecordService;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.TicketOrderStockService;
import com.livehouse.utils.RedisIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 票务订单服务实现类
 */
@Slf4j
@Service
public class TicketOrderServiceImpl extends ServiceImpl<TicketOrderMapper, TicketOrder> implements ITicketOrderService {

    @Autowired
    @Lazy
    private IElectronicTicketService electronicTicketService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IShowService showService;

    @Autowired
    private IRefundRecordService refundRecordService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private TicketOrderStockService ticketOrderStockService;

    @Override
    @Transactional
    public Result simulatePayment(Long orderId) {
        log.info("开始模拟支付，订单ID：{}", orderId);

        // 1. 查询订单
        TicketOrder order = getById(orderId);
        if (order == null) {
            return Result.fail(StatusCode.ORDER_NOT_FOUND);
        }

        if (order.getPayStatus() == 1) {
            return Result.fail(StatusCode.ORDER_ALREADY_PAID);
        }

        if (order.getOrderStatus() == 3) {
            return Result.fail(StatusCode.ORDER_STATUS_ILLEGAL, "订单已取消，无法支付");
        }

        ticketOrderStockService.confirmDbStockOnPayment(order);

        // 2. 更新订单状态为已支付
        order.setPayStatus(1); // 已支付
        order.setOrderStatus(2); // 已完成
        order.setPayTime(LocalDateTime.now());
        boolean updateSuccess = updateById(order);

        if (!updateSuccess) {
            return Result.fail(StatusCode.PAY_FAILED);
        }

        // 3. 清除用户待支付状态
        String userOrderStatusKey = "user:order:status:" + order.getUserId() + ":" + order.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);

        // 4. 生成电子票
        Result ticketResult = electronicTicketService.generateElectronicTickets(orderId);
        if (!ticketResult.getSuccess()) {
            log.error("生成电子票失败，订单ID：{}，错误信息：{}", orderId, ticketResult.getErrorMsg());
            return Result.fail(StatusCode.TICKET_GENERATE_FAILED, "支付成功，但生成电子票失败：" + ticketResult.getErrorMsg());
        }

        log.info("模拟支付成功，订单ID：{}", orderId);
        return Result.ok(ticketResult.getData());
    }

    /**
     * 申请退票
     *
     * 业务规则：
     * 1. 订单必须属于当前用户、已支付、且当前状态为"已完成"（待支付/已取消/已退票都不允许再退票）；
     * 2. 订单下的电子票只要有一张已核销，就拒绝退票（已经检票入场，不支持部分退票场景）；
     * 3. 演出已经开始（开场时间已过）不允许退票；
     * 4. 同一订单只允许发起一次退票（tb_refund_record.order_id 唯一索引兜底）。
     */
    @Override
    @Transactional
    public Result requestRefund(Long orderId, Long userId, String reason) {
        log.info("开始处理退票申请，订单ID：{}，用户ID：{}", orderId, userId);

        // 1. 查询订单并校验归属
        TicketOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail(StatusCode.ORDER_NOT_FOUND);
        }

        if (order.getPayStatus() != 1) {
            return Result.fail(StatusCode.ORDER_NOT_PAID, "订单未支付，无需退票，请直接取消订单");
        }
        if (order.getOrderStatus() == 4) {
            return Result.fail(StatusCode.REFUND_DUPLICATED);
        }
        if (order.getOrderStatus() == 3) {
            return Result.fail(StatusCode.ORDER_STATUS_ILLEGAL, "订单已取消，无法退票");
        }
        if (order.getOrderStatus() != 2) {
            return Result.fail(StatusCode.REFUND_NOT_ALLOWED);
        }

        // 2. 幂等校验：同一订单是否已经存在退票记录（防止重复点击产生多条记录）
        RefundRecord existingRecord = refundRecordService.lambdaQuery()
                .eq(RefundRecord::getOrderId, orderId)
                .one();
        if (existingRecord != null) {
            return Result.fail(StatusCode.REFUND_DUPLICATED,
                    "该订单已提交过退票申请，当前状态：" + describeRefundStatus(existingRecord.getStatus()));
        }

        // 3. 电子票已核销则拒绝退票
        List<ElectronicTicket> tickets = electronicTicketService.lambdaQuery()
                .eq(ElectronicTicket::getOrderId, orderId)
                .list();
        boolean anyVerified = tickets.stream().anyMatch(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 1);
        if (anyVerified) {
            return Result.fail(StatusCode.REFUND_TICKET_USED);
        }

        // 4. 演出已开始则拒绝退票
        Show show = showService.getById(order.getShowId());
        if (show != null && show.getStartTime() != null && !show.getStartTime().isAfter(LocalDateTime.now())) {
            return Result.fail(StatusCode.REFUND_SHOW_STARTED);
        }

        // 5. 落库退票记录（审核中），订单保持「已完成」直至管理员审核
        Long refundRecordId = redisIDGenerator.getId("refund_record");
        RefundRecord record = new RefundRecord();
        record.setId(refundRecordId);
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setShowId(order.getShowId());
        record.setTicketTypeId(order.getTicketTypeId());
        record.setQuantity(order.getQuantity());
        record.setAmount(order.getAmount());
        record.setStatus(RefundRecord.STATUS_PENDING);
        record.setReason(reason);
        record.setCreateTime(LocalDateTime.now());
        refundRecordService.save(record);

        log.info("退票申请已提交待审核，订单ID：{}，退票记录ID：{}", orderId, refundRecordId);
        return Result.ok("退票申请已提交，请等待审核");
    }

    @Override
    public Result getRefundStatus(Long orderId, Long userId) {
        TicketOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail(StatusCode.ORDER_NOT_FOUND);
        }

        RefundRecord record = refundRecordService.lambdaQuery()
                .eq(RefundRecord::getOrderId, orderId)
                .one();
        if (record == null) {
            return Result.fail(StatusCode.REFUND_NOT_FOUND);
        }
        return Result.ok(record);
    }

    private String describeRefundStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case RefundRecord.STATUS_PENDING: return "处理中";
            case RefundRecord.STATUS_SUCCESS: return "审核通过";
            case RefundRecord.STATUS_FAILED: return "审核不通过";
            default: return "未知";
        }
    }
}
