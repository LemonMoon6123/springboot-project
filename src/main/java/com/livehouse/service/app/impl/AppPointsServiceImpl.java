package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.CreditExchange;
import com.livehouse.entity.CreditGoods;
import com.livehouse.entity.CreditLog;
import com.livehouse.mapper.CreditGoodsMapper;
import com.livehouse.mapper.CreditLogMapper;
import com.livehouse.service.app.AppPointsService;
import com.livehouse.service.app.credit.CreditAccountService;
import com.livehouse.vo.app.points.PointsExchangeVo;
import com.livehouse.vo.app.points.PointsGoodsVo;
import com.livehouse.vo.app.points.PointsLogItemVo;
import com.livehouse.vo.app.points.PointsLogListVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppPointsServiceImpl implements AppPointsService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private CreditGoodsMapper creditGoodsMapper;
    @Resource
    private CreditLogMapper creditLogMapper;
    @Resource
    private CreditAccountService creditAccountService;

    @Override
    public List<PointsGoodsVo> shop(Long userId) {
        int available = userId == null ? 0 : creditAccountService.sumAvailableCredits(userId);
        List<CreditGoods> goods = creditGoodsMapper.selectList(new LambdaQueryWrapper<CreditGoods>()
                .eq(CreditGoods::getStatus, 1)
                .orderByAsc(CreditGoods::getId));
        return goods.stream().map(g -> {
            PointsGoodsVo m = new PointsGoodsVo();
            m.setId(String.valueOf(g.getId()));
            m.setIcon(g.getIcon());
            m.setName(g.getName());
            m.setCreditCost(g.getCreditPrice());
            String block = null;
            boolean can = true;
            if (g.getStock() == null || g.getStock() <= 0) {
                can = false;
                block = "库存不足";
            } else if (userId != null && available < g.getCreditPrice()) {
                can = false;
                block = "可用积分不足";
            }
            m.setCanExchange(can);
            m.setBlockReason(block);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public PointsLogListVo logs(Long userId, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        LambdaQueryWrapper<CreditLog> w = new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getStatus, CreditAccountService.STATUS_VALID)
                .orderByDesc(CreditLog::getCreateTime);
        long total = creditLogMapper.selectCount(w);
        List<CreditLog> rows = creditLogMapper.selectList(w.last("LIMIT " + (current - 1) * size + "," + size));
        List<PointsLogItemVo> records = rows.stream().map(l -> {
            PointsLogItemVo r = new PointsLogItemVo();
            r.setId(String.valueOf(l.getId()));
            r.setTitle(l.getRemark() == null ? l.getType() : l.getRemark());
            r.setTime(l.getCreateTime() == null ? null : l.getCreateTime().format(DT));
            int amt = l.getChangeAmount() == null ? 0 : l.getChangeAmount();
            r.setVal((amt >= 0 ? "+" : "") + amt);
            r.setUp(amt >= 0);
            r.setStatus(CreditAccountService.STATUS_VALID);
            r.setStatusText(amt < 0 ? "已扣除" : "已到账");
            return r;
        }).collect(Collectors.toList());
        PointsLogListVo data = new PointsLogListVo();
        data.setRecords(records);
        data.setTotal(total);
        data.setCurrent(current);
        data.setSize(size);
        data.setPages(total == 0 ? 0 : (total + size - 1) / size);
        return data;
    }

    @Override
    public PointsExchangeVo exchange(Long userId, Long goodsId, String idempotentKey) {
        CreditExchange ex = creditAccountService.exchange(userId, goodsId, idempotentKey);
        PointsExchangeVo data = new PointsExchangeVo();
        data.setExchangeId(String.valueOf(ex.getId()));
        data.setRedeemCode(ex.getRedeemCode());
        data.setTip("兑换成功，凭领取码在现场服务台领取");
        return data;
    }
}