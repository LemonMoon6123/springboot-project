package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.TicketType;
import com.livehouse.mapper.TicketTypeMapper;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.*;

/**
 * 票种服务实现类
 * 票种是演出详情页的高频查询数据，使用互斥锁方案解决缓存击穿：
 * 缓存失效瞬间只有一个线程查库重建缓存，其余线程重试，避免大量请求同时压到数据库。
 */
@Service
public class TicketTypeServiceImpl extends ServiceImpl<TicketTypeMapper, TicketType> implements ITicketTypeService {

    @Autowired
    private CacheClient cacheClient;

    @Override
    public List<TicketType> listByShowId(Long showId) {
        // 缓存未命中时用setnx互斥锁保证只有一个线程查库并回填（防缓存击穿）；
        // 查不到的空结果也会被短暂缓存（防缓存穿透）。
        TicketType[] ticketTypes = cacheClient.queryWithMutex(
                CACHE_TICKET_TYPE_KEY, showId, TicketType[].class,
                id -> lambdaQuery()
                        .eq(TicketType::getShowId, id)
                        .orderByAsc(TicketType::getPrice)
                        .list()
                        .toArray(new TicketType[0]),
                CACHE_TICKET_TYPE_TTL, TimeUnit.MINUTES);

        return ticketTypes == null ? Collections.emptyList() : Arrays.asList(ticketTypes);
    }
}
