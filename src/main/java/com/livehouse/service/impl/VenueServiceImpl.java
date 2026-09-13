package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.Venue;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.service.IVenueService;
import org.springframework.stereotype.Service;

/**
 * 场馆服务实现类
 */
@Service
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements IVenueService {

}