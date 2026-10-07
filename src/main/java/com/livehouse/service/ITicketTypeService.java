package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.entity.TicketType;

import java.util.List;

/**
 * 票种服务接口
 */
public interface ITicketTypeService extends IService<TicketType> {

    /**
     * 根据演出ID查询票种列表（互斥锁缓存，防缓存击穿）
     */
    List<TicketType> listByShowId(Long showId);
}