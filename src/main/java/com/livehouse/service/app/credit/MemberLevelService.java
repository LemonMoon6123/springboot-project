package com.livehouse.service.app.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.MemberLevel;
import com.livehouse.entity.UserInfo;
import com.livehouse.mapper.MemberLevelMapper;
import com.livehouse.mapper.UserInfoMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;

@Service
public class MemberLevelService {

    @Resource
    private MemberLevelMapper memberLevelMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Lazy
    @Resource
    private CreditAccountService creditAccountService;

    /** 查询：优先 user_info.total_credits 快照 */
    public int sumTotalCredits(Long userId) {
        UserInfo info = userInfoMapper.selectById(userId);
        if (info != null && info.getTotalCredits() != null) {
            return info.getTotalCredits();
        }
        return creditAccountService.aggregateTotalFromLog(userId);
    }

    public MemberLevel resolveMemberLevel(Long userId) {
        MemberLevel matched = resolveMemberLevelByTotal(sumTotalCredits(userId));
        UserInfo info = userInfoMapper.selectById(userId);
        int legacy = info == null || info.getLevel() == null ? 0 : info.getLevel();
        if (matched.getLevel() < legacy) {
            MemberLevel fallback = levelByNo(legacy);
            if (fallback != null) {
                return fallback;
            }
        }
        return matched;
    }

    public MemberLevel resolveMemberLevelByTotal(int totalCredits) {
        List<MemberLevel> levels = memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getStatus, 1)
                .orderByDesc(MemberLevel::getMinTotalCredits));
        return levels.stream()
                .filter(l -> totalCredits >= l.getMinTotalCredits())
                .max(Comparator.comparing(MemberLevel::getLevel))
                .orElse(levelByNo(0));
    }

    public MemberLevel levelByNo(int level) {
        MemberLevel ml = memberLevelMapper.selectById(level);
        if (ml == null) {
            ml = new MemberLevel();
            ml.setLevel(0);
            ml.setName("普通");
            ml.setDiscountRate(new java.math.BigDecimal("1.00"));
            ml.setPriorityMinutes(0);
            ml.setMinTotalCredits(0);
        }
        return ml;
    }

    public MemberLevel nextLevel(MemberLevel current) {
        return memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getStatus, 1)
                .gt(MemberLevel::getLevel, current.getLevel())
                .orderByAsc(MemberLevel::getLevel))
                .stream().findFirst().orElse(null);
    }

    public void refreshUserLevel(Long userId) {
        creditAccountService.syncCreditSnapshot(userId);
    }
}