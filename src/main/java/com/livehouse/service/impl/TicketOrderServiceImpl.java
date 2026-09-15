package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.dto.Result;
import com.livehouse.entity.TicketOrder;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.ITicketOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Override
    @Transactional
    public Result simulatePayment(Long orderId) {
        log.info("开始模拟支付，订单ID：{}", orderId);

        // 1. 查询订单
        TicketOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (order.getPayStatus() == 1) {
            return Result.fail("订单已支付");
        }

        if (order.getOrderStatus() == 3) {
            return Result.fail("订单已取消，无法支付");
        }

        // 2. 更新订单状态为已支付
        order.setPayStatus(1); // 已支付
        order.setOrderStatus(2); // 已完成
        order.setPayTime(LocalDateTime.now());
        boolean updateSuccess = updateById(order);

        if (!updateSuccess) {
            return Result.fail("支付失败");
        }

        // 3. 清除用户待支付状态
        String userOrderStatusKey = "user:order:status:" + order.getUserId() + ":" + order.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);

        // 4. 生成电子票
        Result ticketResult = electronicTicketService.generateElectronicTickets(orderId);
        if (!ticketResult.getSuccess()) {
            log.error("生成电子票失败，订单ID：{}，错误信息：{}", orderId, ticketResult.getErrorMsg());
            return Result.fail("支付成功，但生成电子票失败：" + ticketResult.getErrorMsg());
        }

        log.info("模拟支付成功，订单ID：{}", orderId);
        return Result.ok(ticketResult.getData());
    }
}