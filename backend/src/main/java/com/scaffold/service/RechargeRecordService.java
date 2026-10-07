package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.PageResult;
import com.scaffold.entity.RechargeRecord;
import com.scaffold.mapper.RechargeRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RechargeRecordService {

    @Autowired
    private RechargeRecordMapper rechargeRecordMapper;

    public void save(Long userId, Double amount, Double balanceAfter, String remark) {
        RechargeRecord row = new RechargeRecord();
        row.setUserId(userId);
        row.setAmount(amount);
        row.setBalanceAfter(balanceAfter);
        row.setRemark(remark == null ? "余额充值" : remark);
        rechargeRecordMapper.insert(row);
    }

    public PageResult<RechargeRecord> page(Long userId, int page, int size) {
        Page<RechargeRecord> result = rechargeRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RechargeRecord>()
                        .eq(RechargeRecord::getUserId, userId)
                        .orderByDesc(RechargeRecord::getId));
        return PageResult.of(result);
    }
}
