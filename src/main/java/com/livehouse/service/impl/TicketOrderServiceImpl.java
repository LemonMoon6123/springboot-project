package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.TicketOrder;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.service.ITicketOrderService;
import org.springframework.stereotype.Service;

/**
 * 票务订单服务实现类
 */
@Service
public class TicketOrderServiceImpl extends ServiceImpl<TicketOrderMapper, TicketOrder> implements ITicketOrderService {

}