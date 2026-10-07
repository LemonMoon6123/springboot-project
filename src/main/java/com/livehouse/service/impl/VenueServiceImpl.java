package com.livehouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.entity.Venue;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.service.IVenueService;
import com.livehouse.utils.CacheClient;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.livehouse.utils.RedisConstants.*;

/**
 * 场馆服务实现类
 * 场馆是典型的读多写少基础数据，查询路径使用：
 * 1. Redisson布隆过滤器 -> 拦截一定不存在的场馆ID（缓存穿透）
 * 2. Redisson读写锁 + 旁路缓存 -> 读共享写互斥（双写一致），缓存与数据库的强一致
 */
@Service
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements IVenueService {

    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据ID查询场馆
     */
    @Override
    public Venue queryVenueById(Long id) {
        // 1.布隆过滤器校验，不存在的ID直接返回，请求根本不会到达数据库（防缓存穿透）
        RBloomFilter<String> bloomFilter = cacheClient.getBloomFilter(BLOOM_VENUE_KEY);
        if (!bloomFilter.contains(id.toString())) {
            return null;
        }

        // 2.布隆判定存在，走读写锁 + 旁路缓存（读锁共享，与更新的写锁互斥）
        return cacheClient.queryWithReadWriteLock(
                LOCK_VENUE_RW_KEY, CACHE_VENUE_KEY, id, Venue.class,
                this::getById, CACHE_VENUE_TTL, TimeUnit.MINUTES);
    }

    /**
     * 更新场馆：写锁内先更新数据库再删除缓存，与读路径互斥，保证双写一致
     * 单条updateById执行即自动提交，提交后才删缓存、最后释放写锁，避免"锁先于事务释放"的窗口
     */
    @Override
    public boolean updateVenue(Venue venue) {
        cacheClient.updateWithReadWriteLock(
                LOCK_VENUE_RW_KEY, CACHE_VENUE_KEY, venue.getId(),
                () -> updateById(venue));
        return true;
    }

    /**
     * 新增场馆：写库后把ID加入布隆过滤器，否则新场馆会被布隆误判为"不存在"
     */
    @Override
    public boolean saveVenue(Venue venue) {
        boolean saved = save(venue);
        cacheClient.getBloomFilter(BLOOM_VENUE_KEY).add(venue.getId().toString());
        return saved;
    }
}
