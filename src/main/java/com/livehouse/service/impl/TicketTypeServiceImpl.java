package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.TicketType;
import com.livehouse.mapper.TicketTypeMapper;
import com.livehouse.service.ITicketTypeService;
import org.springframework.stereotype.Service;

/**
 * 票种服务实现类
 */
@Service
public class TicketTypeServiceImpl extends ServiceImpl<TicketTypeMapper, TicketType> implements ITicketTypeService {

}