package com.livehouse.service.app.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.MemberLevel;
import com.livehouse.service.app.AppMemberService;
import com.livehouse.service.app.credit.CreditAccountService;
import com.livehouse.service.app.credit.MemberLevelService;
import com.livehouse.mapper.MemberLevelMapper;
import com.livehouse.vo.app.member.MemberBenefitVo;
import com.livehouse.vo.app.member.MemberInfoVo;
import com.livehouse.vo.app.member.MemberLevelCardVo;
import com.livehouse.vo.app.member.MemberLevelProgressVo;
import com.livehouse.vo.app.member.MemberPerkVo;
import com.livehouse.vo.app.member.MemberPointsSummaryVo;
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
    public MemberInfoVo memberInfo(Long userId) {
        int totalCredits = memberLevelService.sumTotalCredits(userId);
        MemberLevel current = memberLevelService.resolveMemberLevel(userId);
        MemberLevel next = memberLevelService.nextLevel(current);

        List<MemberLevel> all = memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getStatus, 1)
                .orderByAsc(MemberLevel::getLevel));

        List<MemberLevelCardVo> levels = new ArrayList<>();
        for (MemberLevel lv : all) {
            MemberLevelCardVo card = new MemberLevelCardVo();
            boolean isCurrent = Objects.equals(lv.getLevel(), current.getLevel());
            boolean passed = totalCredits >= lv.getMinTotalCredits();
            card.setLevel(lv.getLevel());
            card.setName(lv.getLevel() == 0 ? "普通用户" : lv.getName());
            card.setTag(buildTag(lv, next, totalCredits, isCurrent));
            card.setLight(passed || isCurrent);
            card.setCur(isCurrent);
            card.setDiscountText(discountText(lv.getDiscountRate()));
            card.setNeedText(lv.getMinTotalCredits() == 0 ? "无门槛" : String.valueOf(lv.getMinTotalCredits()));
            card.setMinTotalCredits(lv.getMinTotalCredits());
            card.setDiscountRate(lv.getDiscountRate());
            card.setPriorityMinutes(lv.getPriorityMinutes());
            card.setProgress(progress(totalCredits, lv));
            card.setNote(buildNote(lv, current, totalCredits));
            card.setPerks(parsePerks(lv.getBenefitsJson()));
            levels.add(card);
        }

        MemberPointsSummaryVo points = new MemberPointsSummaryVo();
        int available = creditAccountService.sumAvailableCredits(userId);
        points.setAvailable(available);
        points.setTotal(totalCredits);
        points.setMonthGain(creditAccountService.monthGain(userId));
        if (next != null) {
            points.setNextLevelName(next.getName() + " Lv." + next.getLevel());
            points.setNextLevelNeed(Math.max(0, next.getMinTotalCredits() - totalCredits));
        } else {
            points.setNextLevelName(null);
            points.setNextLevelNeed(0);
        }

        List<MemberBenefitVo> benefits = new ArrayList<>();
        if (current.getPriorityMinutes() != null && current.getPriorityMinutes() > 0) {
            MemberBenefitVo b = new MemberBenefitVo();
            b.setIcon("bolt");
            b.setName("优先购票");
            b.setDesc("热门演出提前 " + current.getPriorityMinutes() + " 分钟开售");
            benefits.add(b);
        }

        MemberInfoVo data = new MemberInfoVo();
        data.setCurrentLevel(current.getLevel());
        data.setCurrentLevelName(current.getName());
        data.setTotalCredits(totalCredits);
        data.setLevels(levels);
        data.setBenefits(benefits);
        data.setPoints(points);
        return data;
    }

    private static String discountText(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ONE) >= 0) {
            return "原价";
        }
        return rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + " 折";
    }

    private static MemberLevelProgressVo progress(int total, MemberLevel lv) {
        int target = lv.getMinTotalCredits() == null ? 0 : lv.getMinTotalCredits();
        int percent = target <= 0 ? 100 : Math.min(100, total * 100 / target);
        MemberLevelProgressVo p = new MemberLevelProgressVo();
        p.setCurrent(total);
        p.setTarget(target);
        p.setPercent(percent);
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

    private static List<MemberPerkVo> parsePerks(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            List<MemberPerkVo> perks = new ArrayList<>();
            for (Object o : arr) {
                JSONObject obj = JSONUtil.parseObj(o);
                MemberPerkVo p = new MemberPerkVo();
                p.setIcon(obj.getStr("icon"));
                p.setName(obj.getStr("name"));
                p.setDesc(obj.getStr("desc"));
                p.setTitle(obj.getStr("title"));
                p.setText(obj.getStr("text"));
                perks.add(p);
            }
            return perks;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}