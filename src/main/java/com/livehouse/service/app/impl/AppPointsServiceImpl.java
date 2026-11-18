package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.CreditExchange;
import com.livehouse.entity.CreditGoods;
import com.livehouse.entity.CreditLog;
import com.livehouse.mapper.CreditGoodsMapper;
import com.livehouse.mapper.CreditLogMapper;
import com.livehouse.service.app.AppPointsService;
import com.livehouse.service.app.credit.CreditAccountService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public List<Map<String, Object>> shop(Long userId) {
        int available = userId == null ? 0 : creditAccountService.sumAvailableCredits(userId);
        List<CreditGoods> goods = creditGoodsMapper.selectList(new LambdaQueryWrapper<CreditGoods>()
                .eq(CreditGoods::getStatus, 1)
                .orderByAsc(CreditGoods::getId));
        return goods.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(g.getId()));
            m.put("icon", g.getIcon());
            m.put("name", g.getName());
            m.put("creditCost", g.getCreditPrice());
            String block = null;
            boolean can = true;
            if (g.getStock() == null || g.getStock() <= 0) {
                can = false;
                block = "库存不足";
            } else if (userId != null && available < g.getCreditPrice()) {
                can = false;
                block = "可用积分不足";
            }
            m.put("canExchange", can);
            m.put("blockReason", block);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> logs(Long userId, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        LambdaQueryWrapper<CreditLog> w = new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .orderByDesc(CreditLog::getCreateTime);
        long total = creditLogMapper.selectCount(w);
        List<CreditLog> rows = creditLogMapper.selectList(w.last("LIMIT " + (current - 1) * size + "," + size));
        List<Map<String, Object>> records = rows.stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", String.valueOf(l.getId()));
            r.put("title", l.getRemark() == null ? l.getType() : l.getRemark());
            r.put("time", l.getCreateTime() == null ? null : l.getCreateTime().format(DT));
            int amt = l.getChangeAmount() == null ? 0 : l.getChangeAmount();
            r.put("val", (amt >= 0 ? "+" : "") + amt);
            r.put("up", amt >= 0);
            return r;
        }).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("pages", total == 0 ? 0 : (total + size - 1) / size);
        return data;
    }

    @Override
    public Map<String, Object> exchange(Long userId, Long goodsId, String idempotentKey) {
        CreditExchange ex = creditAccountService.exchange(userId, goodsId, idempotentKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exchangeId", String.valueOf(ex.getId()));
        data.put("redeemCode", ex.getRedeemCode());
        data.put("tip", "兑换成功，凭领取码在现场服务台领取");
        return data;
    }
}