package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.CheckInRecord;
import com.livehouse.mapper.CheckInRecordMapper;
import com.livehouse.service.ICheckInRecordService;
import org.springframework.stereotype.Service;

/**
 * 核销记录服务实现
 */
@Service
public class CheckInRecordServiceImpl extends ServiceImpl<CheckInRecordMapper, CheckInRecord> 
        implements ICheckInRecordService {
}