package com.livehouse.service.app;

import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.OrderAudience;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketOrder;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.mapper.OrderAudienceMapper;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.utils.RedisIDGenerator;
import com.livehouse.utils.TicketEncryptionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.mapper.ElectronicTicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付成功统一处理：订单状态、entry_status、按观演人生成电子票。
 */
@Slf4j
@Service
public class PaymentSuccessHandler extends ServiceImpl<ElectronicTicketMapper, ElectronicTicket> {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private OrderAudienceMapper orderAudienceMapper;

    @Autowired
    private IShowService showService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private TicketEncryptionUtils ticketEncryptionUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AppMessageService appMessageService;

    @Transactional
    public void handlePaymentSuccess(Long orderId) {
        TicketOrder order = ticketOrderService.getById(orderId);
        if (order == null) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        if (order.getPayStatus() != null && order.getPayStatus() == 1) {
            log.info("订单已支付，幂等跳过，orderId={}", orderId);
            return;
        }
        if (order.getOrderStatus() == null || order.getOrderStatus() != 1) {
            throw new StatusCodeException(StatusCode.ORDER_STATUS_ILLEGAL);
        }

        order.setPayStatus(1);
        order.setOrderStatus(2);
        order.setPayTime(LocalDateTime.now());
        order.setEntryStatus(0);
        order.setUpdateTime(LocalDateTime.now());
        if (!ticketOrderService.updateById(order)) {
            throw new StatusCodeException(StatusCode.PAY_FAILED);
        }

        String userOrderStatusKey = "user:order:status:" + order.getUserId() + ":" + order.getTicketTypeId();
        stringRedisTemplate.delete(userOrderStatusKey);

        generateTickets(order);
        appMessageService.insertMessage(order.getUserId(), "order", "订单支付成功",
                "你的订单已支付成功，电子票已发放至票夹。", "order", String.valueOf(order.getId()));
    }

    private void generateTickets(TicketOrder order) {
        List<ElectronicTicket> existing = lambdaQuery().eq(ElectronicTicket::getOrderId, order.getId()).list();
        if (!existing.isEmpty()) {
            return;
        }

        List<OrderAudience> audiences = orderAudienceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderAudience>()
                        .eq(OrderAudience::getOrderId, order.getId())
                        .orderByAsc(OrderAudience::getId));

        Show show = showService.getById(order.getShowId());
        String defaultGate = "A";
        if (show != null && show.getGateConfig() != null && !show.getGateConfig().isEmpty()) {
            defaultGate = show.getGateConfig().split(",")[0].trim();
        }

        List<ElectronicTicket> tickets = new ArrayList<>();
        int qty = order.getQuantity();
        for (int i = 0; i < qty; i++) {
            OrderAudience oa = i < audiences.size() ? audiences.get(i) : null;

            Long ticketId = redisIDGenerator.getId("electronic_ticket");
            String originalTicketCode = "ET" + ticketId;
            String encrypted = ticketEncryptionUtils.encryptTicketCode(originalTicketCode);

            String shortCode = generateUniqueShortCode(order.getShowId());
            String displayCode = formatDisplayCode(ticketId);

            ElectronicTicket ticket = new ElectronicTicket();
            ticket.setId(ticketId);
            ticket.setVerifyCode(encrypted);
            ticket.setDisplayCode(displayCode);
            ticket.setShortCode(shortCode);
            ticket.setOrderId(order.getId());
            ticket.setUserId(order.getUserId());
            ticket.setShowId(order.getShowId());
            ticket.setTicketTypeId(order.getTicketTypeId());
            ticket.setVerifyStatus(0);
            ticket.setGate(defaultGate);
            ticket.setCreateTime(LocalDateTime.now());
            if (oa != null) {
                ticket.setAudienceId(oa.getAudienceId());
                ticket.setAudienceName(oa.getAudienceName());
                ticket.setAudienceIdCardMask(oa.getIdCardMask());
                oa.setTicketId(ticketId);
                orderAudienceMapper.updateById(oa);
            }
            tickets.add(ticket);
        }
        saveBatch(tickets);
    }

    // 随即碰撞20次生成固定6位随机数
    private String generateUniqueShortCode(Long showId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            long cnt = lambdaQuery().eq(ElectronicTicket::getShowId, showId).eq(ElectronicTicket::getShortCode, code).count();
            if (cnt == 0) {
                return code;
            }
        }
        long id = redisIDGenerator.getId("short_code");
        return String.format("%06d", id % 1_000_000); // 兜底处理，取后六位
    }

    // 取ID生成器后12位作为电子票展示码
    private static String formatDisplayCode(long ticketId) {
        String digits = String.format("%012d", Math.abs(ticketId % 1_000_000_000_000L));
        return digits.substring(0, 4) + " " + digits.substring(4, 8) + " " + digits.substring(8, 12);
    }
}