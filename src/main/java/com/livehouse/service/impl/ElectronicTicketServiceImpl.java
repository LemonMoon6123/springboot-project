package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.dto.Result;
import com.livehouse.entity.CheckInRecord;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.TicketOrder;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.service.ICheckInRecordService;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.utils.RedisIDGenerator;
import com.livehouse.utils.TicketEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 电子票服务实现
 */
@Slf4j
@Service
public class ElectronicTicketServiceImpl extends ServiceImpl<ElectronicTicketMapper, ElectronicTicket> 
        implements IElectronicTicketService {

    @Autowired
    private ITicketOrderService ticketOrderService;

    @Autowired
    private ICheckInRecordService checkInRecordService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TicketEncryptionUtils ticketEncryptionUtils;

    @Override
    @Transactional
    public Result generateElectronicTickets(Long orderId) {
        log.info("开始为订单生成电子票，订单ID：{}", orderId);

        // 1. 查询订单信息
        TicketOrder order = ticketOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (order.getPayStatus() != 1) {
            return Result.fail("订单未支付，无法生成电子票");
        }

        // 2. 检查是否已生成电子票
        List<ElectronicTicket> existingTickets = lambdaQuery()
                .eq(ElectronicTicket::getOrderId, orderId)
                .list();
        
        if (!existingTickets.isEmpty()) {
            log.info("订单已生成电子票，订单ID：{}，电子票数量：{}", orderId, existingTickets.size());
            return Result.ok(existingTickets);
        }

        // 3. 批量生成电子票
        List<ElectronicTicket> electronicTickets = new ArrayList<>();
        for (int i = 0; i < order.getQuantity(); i++) {
            // 生成雪花算法电子票号
            Long ticketId = redisIDGenerator.getId("electronic_ticket");
            String originalTicketCode = "ET" + ticketId;

            // 对票务验证码进行加密，增强安全性
            String encryptedTicketCode = ticketEncryptionUtils.encryptTicketCode(originalTicketCode);

            ElectronicTicket ticket = new ElectronicTicket();
            ticket.setId(ticketId);
            ticket.setVerifyCode(encryptedTicketCode);  // 存储加密后的验证码
            ticket.setOrderId(orderId);
            ticket.setUserId(order.getUserId());
            ticket.setShowId(order.getShowId());
            ticket.setTicketTypeId(order.getTicketTypeId());
            ticket.setVerifyStatus(0);
            ticket.setCreateTime(LocalDateTime.now());

            electronicTickets.add(ticket);
            
            log.debug("生成电子票，ID：{}，原始码：{}，加密码长度：{}", ticketId, originalTicketCode, encryptedTicketCode.length());
        }

        // 4. 批量保存到数据库
        boolean saveSuccess = saveBatch(electronicTickets);
        if (!saveSuccess) {
            return Result.fail("生成电子票失败");
        }

        log.info("电子票生成成功，订单ID：{}，生成数量：{}", orderId, electronicTickets.size());
        return Result.ok(electronicTickets);
    }

    /**
     *
     * ticketCode 电子票号，就是verify_code，verify_code = “ET” + 电子票id
     *
     * 实际业务流程：
     * 用户买票 → 获得电子票号（相当于纸质票）
     * 到达现场 → 出示电子票号（相当于出示票据）
     * 工作人员核销 → 扫码验证（相当于撕票/打孔）
     * 核销成功 → 允许入场（相当于通过检票口）
     * 防重复使用 → 已核销票无法再用（相当于撕掉的票不能重复使用）
     */
    @Override
    @Transactional
    public Result verifyTicket(String encryptedTicketCode) {
        log.info("开始核销电子票，加密票号长度：{}", encryptedTicketCode.length());

        try {
            // 1. 先解密票号，验证是否是有效的票
            TicketEncryptionUtils.TicketValidationResult validationResult = 
                    ticketEncryptionUtils.validateEncryptedTicketCode(encryptedTicketCode);
            
            if (!validationResult.isValid()) {
                log.warn("票号验证失败：{}", validationResult.getErrorMessage());
                return Result.fail(validationResult.getErrorMessage());
            }

            String originalTicketCode = validationResult.getOriginalTicketCode();
            log.info("票号解密成功，原始票号：{}", originalTicketCode);

            // 2. 用加密后的票号查询数据库（数据库存的就是加密的）
            ElectronicTicket ticket = lambdaQuery()
                    .eq(ElectronicTicket::getVerifyCode, encryptedTicketCode)
                    .one();

            if (ticket == null) {
                return Result.fail("电子票不存在");
            }

            if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 1) {
                return Result.fail("电子票已核销");
            }

            if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 2) {
                return Result.fail("该电子票已退票作废，无法核销");
            }

            // 3. 用加密票号计算BitMap位置（更安全，基于票号而非ID）
            String bitmapKey = "ticket:verify:" + ticket.getShowId();
            long ticketPosition = ticketEncryptionUtils.calculateBitMapOffset(encryptedTicketCode);

            // 4. 原子性设置BitMap标记（核心逻辑）
            Boolean previousValue = stringRedisTemplate.opsForValue().setBit(bitmapKey, ticketPosition, true);
            
            if (Boolean.TRUE.equals(previousValue)) {
                log.warn("电子票已核销，原始票号：{}", originalTicketCode);
                return Result.fail("电子票已经被使用过！");
            }

            try {
                // 5. 更新数据库状态
                ticket.setVerifyStatus(1);
                ticket.setVerifyTime(LocalDateTime.now());
                updateById(ticket);

                // 6. 记录核销日志
                CheckInRecord record = new CheckInRecord();
                record.setTicketId(ticket.getId());
                record.setUserId(ticket.getUserId());
                record.setShowId(ticket.getShowId());
                record.setCheckTime(LocalDateTime.now());
                checkInRecordService.save(record);

                log.info("电子票核销成功，原始票号：{}", originalTicketCode);
                return Result.ok("核销成功");

            } catch (Exception e) {
                // 异常回滚：清除BitMap标记
                stringRedisTemplate.opsForValue().setBit(bitmapKey, ticketPosition, false);
                log.error("电子票核销失败，已回滚，原始票号：{}", originalTicketCode, e);
                return Result.fail("核销失败");
            }

        } catch (Exception e) {
            log.error("票号解密或验证异常：{}", e.getMessage(), e);
            return Result.fail("票号无效：" + e.getMessage());
        }
    }

    @Override
    public Result getTicketByCode(String ticketCode) {
        ElectronicTicket ticket = lambdaQuery()
                .eq(ElectronicTicket::getVerifyCode, ticketCode)
                .one();

        if (ticket == null) {
            return Result.fail("电子票不存在");
        }

        return Result.ok(ticket);
    }

    @Override
    public Result queryUserTickets(Long userId) {
        List<ElectronicTicket> tickets = lambdaQuery()
                .eq(ElectronicTicket::getUserId, userId)
                .orderByDesc(ElectronicTicket::getCreateTime)
                .list();
        return Result.ok(tickets);
    }

}