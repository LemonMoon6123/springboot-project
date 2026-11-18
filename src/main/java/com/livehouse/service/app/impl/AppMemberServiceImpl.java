package com.livehouse.service.app.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.MemberLevel;
import com.livehouse.service.app.AppMemberService;
import com.livehouse.service.app.credit.CreditAccountService;
import com.livehouse.service.app.credit.MemberLevelService;
import com.livehouse.mapper.MemberLevelMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AppMemberServiceImpl implements AppMemberService {

    @Resource
    private MemberLevelMapper memberLevelMapper;
    @Resource
    private MemberLevelService memberLevelService;
    @Resource
    private CreditAccountService creditAccountService;

    @Override
    public Map<String, Object> memberInfo(Long userId) {
        int totalCredits = memberLevelService.sumTotalCredits(userId);
        MemberLevel current = memberLevelService.resolveMemberLevel(userId);
        MemberLevel next = memberLevelService.nextLevel(current);

        List<MemberLevel> all = memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getStatus, 1)
                .orderByAsc(MemberLevel::getLevel));

        List<Map<String, Object>> levels = new ArrayList<>();
        for (MemberLevel lv : all) {
            Map<String, Object> card = new LinkedHashMap<>();
            boolean isCurrent = Objects.equals(lv.getLevel(), current.getLevel());
            boolean passed = totalCredits >= lv.getMinTotalCredits();
            card.put("level", lv.getLevel());
            card.put("name", lv.getLevel() == 0 ? "普通用户" : lv.getName());
            card.put("tag", buildTag(lv, next, totalCredits, isCurrent));
            card.put("light", passed || isCurrent);
            card.put("cur", isCurrent);
            card.put("discountText", discountText(lv.getDiscountRate()));
            card.put("needText", lv.getMinTotalCredits() == 0 ? "无门槛" : String.valueOf(lv.getMinTotalCredits()));
            card.put("minTotalCredits", lv.getMinTotalCredits());
            card.put("discountRate", lv.getDiscountRate());
            card.put("priorityMinutes", lv.getPriorityMinutes());
            card.put("progress", progress(totalCredits, lv));
            card.put("note", buildNote(lv, current, totalCredits));
            card.put("perks", parsePerks(lv.getBenefitsJson()));
            levels.add(card);
        }

        Map<String, Object> points = new LinkedHashMap<>();
        int available = creditAccountService.sumAvailableCredits(userId);
        int expiring = creditAccountService.expiringSoon(userId);
        points.put("available", available);
        points.put("total", totalCredits);
        points.put("monthGain", creditAccountService.monthGain(userId));
        points.put("expiringSoon", expiring);
        if (expiring > 0) {
            points.put("expiringText", expiring + " 积分将于未来三个月内过期");
        } else {
            points.put("expiringText", null);
        }
        if (next != null) {
            points.put("nextLevelName", next.getName() + " Lv." + next.getLevel());
            points.put("nextLevelNeed", Math.max(0, next.getMinTotalCredits() - totalCredits));
        } else {
            points.put("nextLevelName", null);
            points.put("nextLevelNeed", 0);
        }

        List<Map<String, Object>> benefits = new ArrayList<>();
        if (current.getPriorityMinutes() != null && current.getPriorityMinutes() > 0) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("icon", "bolt");
            b.put("name", "优先购票");
            b.put("desc", "热门演出提前 " + current.getPriorityMinutes() + " 分钟开售");
            benefits.add(b);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentLevel", current.getLevel());
        data.put("currentLevelName", current.getName());
        data.put("totalCredits", totalCredits);
        data.put("levels", levels);
        data.put("benefits", benefits);
        data.put("points", points);
        return data;
    }

    private static String discountText(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ONE) >= 0) {
            return "原价";
        }
        return rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + " 折";
    }

    private static Map<String, Object> progress(int total, MemberLevel lv) {
        int target = lv.getMinTotalCredits() == null ? 0 : lv.getMinTotalCredits();
        int percent = target <= 0 ? 100 : Math.min(100, total * 100 / target);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("current", total);
        p.put("target", target);
        p.put("percent", percent);
        return p;
    }

    private static String buildTag(MemberLevel lv, MemberLevel next, int total, boolean isCurrent) {
        if (isCurrent) {
            return "当前";
        }
        if (total >= lv.getMinTotalCredits()) {
            return "已解锁";
        }
        int need = lv.getMinTotalCredits() - total;
        return need > 0 ? "还差 " + need : "基础";
    }

    private static String buildNote(MemberLevel lv, MemberLevel current, int total) {
        if (Objects.equals(lv.getLevel(), current.getLevel())) {
            return "当前等级为 Lv." + current.getLevel() + " " + current.getName();
        }
        if (total >= lv.getMinTotalCredits()) {
            return "已越过该等级门槛，当前等级为 Lv." + current.getLevel() + " " + current.getName();
        }
        return "还差 " + (lv.getMinTotalCredits() - total) + " 积分解锁该等级";
    }

  private static List<Map<String, Object>> parsePerks(String json) {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      JSONArray arr = JSONUtil.parseArray(json);
      List<Map<String, Object>> perks = new ArrayList<>();
      for (Object o : arr) {
        perks.add(JSONUtil.parseObj(o));
      }
      return perks;
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}