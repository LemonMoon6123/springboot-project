package com.livehouse.service.app.credit;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class CreditAccountService {

    @Resource
    private CreditLogMapper creditLogMapper;
    @Resource
    private CreditGoodsMapper creditGoodsMapper;
    @Resource
    private CreditExchangeMapper creditExchangeMapper;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private ShowMapper showMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private LivehouseProperties livehouseProperties;
    @Resource
    private MemberLevelService memberLevelService;

    public int sumTotalCredits(Long userId) {
        List<CreditLog> logs = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .gt(CreditLog::getChangeAmount, 0)
                .in(CreditLog::getStatus, 0, 1));
        return logs.stream().mapToInt(l -> l.getChangeAmount() == null ? 0 : l.getChangeAmount()).sum();
    }

    public int sumAvailableCredits(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<CreditLog> logs = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, 1));
        return logs.stream()
                .filter(l -> l.getExpireTime() == null || l.getExpireTime().isAfter(now))
                .mapToInt(l -> l.getChangeAmount() == null ? 0 : l.getChangeAmount())
                .sum();
    }

    public int monthGain(Long userId) {
        LocalDateTime start = YearMonth.now().atDay(1).atStartOfDay();
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .gt(CreditLog::getChangeAmount, 0)
                .ge(CreditLog::getCreateTime, start))
                .stream().mapToInt(CreditLog::getChangeAmount).sum();
    }

    public int expiringSoon(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusMonths(3);
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, 1)
                .gt(CreditLog::getChangeAmount, 0)
                .isNotNull(CreditLog::getExpireTime)
                .le(CreditLog::getExpireTime, horizon)
                .gt(CreditLog::getExpireTime, now))
                .stream().mapToInt(CreditLog::getChangeAmount).sum();
    }

    @Transactional
    public void grantPendingAfterVerify(Long ticketId) {
        if (creditLogMapper.selectCount(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getBizId, String.valueOf(ticketId))
                .eq(CreditLog::getType, "purchase")) > 0) {
            return;
        }
        ElectronicTicket ticket = electronicTicketMapper.selectById(ticketId);
        if (ticket == null || ticket.getVerifyStatus() == null || ticket.getVerifyStatus() != 1) {
            return;
        }
        TicketOrder order = ticketOrderMapper.selectById(ticket.getOrderId());
        if (order == null || order.getPayAmount() == null) {
            return;
        }
        int qty = order.getQuantity() == null || order.getQuantity() <= 0 ? 1 : order.getQuantity();
        BigDecimal ticketPay = order.getPayAmount()
                .subtract(order.getServiceFee() == null ? BigDecimal.ZERO : order.getServiceFee())
                .divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
        BigDecimal rate = livehouseProperties.getCredit().getRatePerYuan();
        int points = ticketPay.multiply(rate).setScale(0, RoundingMode.HALF_UP).intValue();
        if (points <= 0) {
            return;
        }

        String title = "观演";
        Show show = showMapper.selectById(ticket.getShowId());
        if (show != null && show.getPerformanceId() != null) {
            Performance p = performanceMapper.selectById(show.getPerformanceId());
            if (p != null) {
                title = p.getTitle();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int delayHours = livehouseProperties.getCredit().getIssueDelayHours();
        CreditLog log = new CreditLog();
        log.setUserId(ticket.getUserId());
        log.setChangeAmount(points);
        log.setBalance(sumAvailableCredits(ticket.getUserId()));
        log.setType("purchase");
        log.setStatus(0);
        log.setReleaseTime(now.plusHours(delayHours));
        log.setBizId(String.valueOf(ticketId));
        log.setRemark("购票获得 · " + title);
        log.setCreateTime(now);
        creditLogMapper.insert(log);

        UserInfo info = userInfoMapper.selectById(ticket.getUserId());
        if (info != null) {
            info.setWatchCount((info.getWatchCount() == null ? 0 : info.getWatchCount()) + 1);
            userInfoMapper.updateById(info);
        }
        memberLevelService.refreshUserLevel(ticket.getUserId());
    }

    @Transactional
    public void releaseDueCredits() {
        LocalDateTime now = LocalDateTime.now();
        List<CreditLog> pending = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getStatus, 0)
                .le(CreditLog::getReleaseTime, now));
        for (CreditLog log : pending) {
            int available = sumAvailableCredits(log.getUserId());
            log.setStatus(1);
            log.setBalance(available + log.getChangeAmount());
            int months = livehouseProperties.getCredit().getExpireMonths();
            log.setExpireTime(now.plusMonths(months));
            creditLogMapper.updateById(log);
            memberLevelService.refreshUserLevel(log.getUserId());
        }
    }

    @Transactional
    public void expireCredits() {
        LocalDateTime now = LocalDateTime.now();
        List<CreditLog> expired = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getStatus, 1)
                .gt(CreditLog::getChangeAmount, 0)
                .isNotNull(CreditLog::getExpireTime)
                .lt(CreditLog::getExpireTime, now));
        for (CreditLog log : expired) {
            log.setStatus(2);
            creditLogMapper.updateById(log);
            int available = sumAvailableCredits(log.getUserId());
            CreditLog neg = new CreditLog();
            neg.setUserId(log.getUserId());
            neg.setChangeAmount(-log.getChangeAmount());
            neg.setBalance(Math.max(0, available));
            neg.setType("expire");
            neg.setStatus(1);
            neg.setRemark("积分过期");
            neg.setBizId("expire:" + log.getId());
            neg.setCreateTime(now);
            creditLogMapper.insert(neg);
            memberLevelService.refreshUserLevel(log.getUserId());
        }
    }

    @Transactional
    public CreditExchange exchange(Long userId, Long goodsId, String idempotentKey) {
        if (idempotentKey != null) {
            CreditExchange existing = creditExchangeMapper.selectOne(new LambdaQueryWrapper<CreditExchange>()
                    .eq(CreditExchange::getIdempotentKey, idempotentKey));
            if (existing != null) {
                return existing;
            }
        }
        CreditGoods goods = creditGoodsMapper.selectById(goodsId);
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            throw new StatusCodeException(StatusCode.GOODS_SOLD_OUT);
        }
        if (goods.getStock() == null || goods.getStock() <= 0) {
            throw new StatusCodeException(StatusCode.GOODS_SOLD_OUT);
        }
        int available = sumAvailableCredits(userId);
        if (available < goods.getCreditPrice()) {
            throw new StatusCodeException(StatusCode.CREDITS_NOT_ENOUGH);
        }

        int stockRows = creditGoodsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CreditGoods>()
                        .setSql("stock = stock - 1")
                        .eq("id", goodsId)
                        .gt("stock", 0));
        if (stockRows <= 0) {
            throw new StatusCodeException(StatusCode.GOODS_SOLD_OUT);
        }

        int newBalance = available - goods.getCreditPrice();
        CreditLog neg = new CreditLog();
        neg.setUserId(userId);
        neg.setChangeAmount(-goods.getCreditPrice());
        neg.setBalance(newBalance);
        neg.setType("exchange");
        neg.setStatus(1);
        neg.setRemark("积分兑换 · " + goods.getName());
        neg.setCreateTime(LocalDateTime.now());
        creditLogMapper.insert(neg);

        CreditExchange ex = new CreditExchange();
        ex.setUserId(userId);
        ex.setGoodsId(goodsId);
        ex.setCreditCost(goods.getCreditPrice());
        ex.setRedeemCode("LH" + RandomUtil.randomStringUpper(6));
        ex.setStatus(0);
        ex.setIdempotentKey(idempotentKey);
        ex.setCreateTime(LocalDateTime.now());
        creditExchangeMapper.insert(ex);

        UserInfo info = userInfoMapper.selectById(userId);
        if (info != null) {
            info.setCredits(newBalance);
            userInfoMapper.updateById(info);
        }
        return ex;
    }
}