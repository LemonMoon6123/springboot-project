package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.RefundRecord;
import com.livehouse.mapper.RefundRecordMapper;
import com.livehouse.service.IRefundRecordService;
import org.springframework.stereotype.Service;

/**
 * 退票记录服务实现类
 */
@Service
public class RefundRecordServiceImpl extends ServiceImpl<RefundRecordMapper, RefundRecord> implements IRefundRecordService {

}
