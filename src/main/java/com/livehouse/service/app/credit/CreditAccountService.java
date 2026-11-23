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

@Service
public class CreditAccountService {

    public static final int STATUS_INVALID = 0;
    public static final int STATUS_VALID = 1;

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

    /** 查询：优先 user_info 快照，无行时从流水聚合 */
    public int sumAvailableCredits(Long userId) {
        UserInfo info = userInfoMapper.selectById(userId);
        if (info != null && info.getCredits() != null) {
            return info.getCredits();
        }
        return aggregateAvailableFromLog(userId);
    }

    int aggregateAvailableFromLog(Long userId) {
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, STATUS_VALID))
                .stream().mapToInt(l -> l.getChangeAmount() == null ? 0 : l.getChangeAmount()).sum();
    }

    int aggregateTotalFromLog(Long userId) {
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, STATUS_VALID)
                .gt(CreditLog::getChangeAmount, 0))
                .stream().mapToInt(l -> l.getChangeAmount() == null ? 0 : l.getChangeAmount()).sum();
    }

    /** 流水写入后调用：重算并写回 credits、total_credits，必要时升级 level */
    @Transactional
    public void syncCreditSnapshot(Long userId) {
        int available = aggregateAvailableFromLog(userId);
        int total = aggregateTotalFromLog(userId);
        UserInfo info = userInfoMapper.selectById(userId);
        if (info == null) {
            return;
        }
        info.setCredits(available);
        info.setTotalCredits(total);
        MemberLevel resolved = memberLevelService.resolveMemberLevelByTotal(total);
        int current = info.getLevel() == null ? 0 : info.getLevel();
        int target = resolved.getLevel() == null ? 0 : resolved.getLevel();
        if (target > current) {
            info.setLevel(target);
        }
        userInfoMapper.updateById(info);
    }

    public int monthGain(Long userId) {
        LocalDateTime start = YearMonth.now().atDay(1).atStartOfDay();
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, STATUS_VALID)
                .gt(CreditLog::getChangeAmount, 0)
                .ge(CreditLog::getCreateTime, start))
                .stream().mapToInt(CreditLog::getChangeAmount).sum();
    }

    /**
     * 核销成功后即时发放：累计积分与可用积分同时入账（支付不发积分）。
     *
     * @return 本次发放积分数，0 表示未发放
     */
    @Transactional
    public int grantAfterVerify(Long ticketId) {
        if (creditLogMapper.selectCount(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getBizId, String.valueOf(ticketId))
                .eq(CreditLog::getType, "purchase")
                .eq(CreditLog::getStatus, STATUS_VALID)) > 0) {
            return 0;
        }
        ElectronicTicket ticket = electronicTicketMapper.selectById(ticketId);
        if (ticket == null || ticket.getVerifyStatus() == null || ticket.getVerifyStatus() != 1) {
            return 0;
        }
        TicketOrder order = ticketOrderMapper.selectById(ticket.getOrderId());
        if (order == null || order.getPayAmount() == null) {
            return 0;
        }
        int qty = order.getQuantity() == null || order.getQuantity() <= 0 ? 1 : order.getQuantity();
        BigDecimal ticketPay = order.getPayAmount()
                .subtract(order.getServiceFee() == null ? BigDecimal.ZERO : order.getServiceFee())
                .divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
        BigDecimal rate = livehouseProperties.getCredit().getRatePerYuan();
        int points = ticketPay.multiply(rate).setScale(0, RoundingMode.HALF_UP).intValue();
        if (points <= 0) {
            return 0;
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
        CreditLog log = new CreditLog();
        log.setUserId(ticket.getUserId());
        log.setChangeAmount(points);
        log.setType("purchase");
        log.setStatus(STATUS_VALID);
        log.setBizId(String.valueOf(ticketId));
        log.setRemark("购票获得 · " + title);
        log.setCreateTime(now);
        creditLogMapper.insert(log);

        UserInfo info = userInfoMapper.selectById(ticket.getUserId());
        if (info != null) {
            info.setWatchCount((info.getWatchCount() == null ? 0 : info.getWatchCount()) + 1);
            userInfoMapper.updateById(info);
        }
        syncCreditSnapshot(ticket.getUserId());
        return points;
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

        CreditLog neg = new CreditLog();
        neg.setUserId(userId);
        neg.setChangeAmount(-goods.getCreditPrice());
        neg.setType("exchange");
        neg.setStatus(STATUS_VALID);
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

        syncCreditSnapshot(userId);
        return ex;
    }
}