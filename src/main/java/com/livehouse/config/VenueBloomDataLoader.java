package com.livehouse.config;

import com.livehouse.entity.Venue;
import com.livehouse.service.IVenueService;
import com.livehouse.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.livehouse.utils.RedisConstants.BLOOM_VENUE_KEY;

/**
 * 项目启动时把所有场馆ID加载到Redisson布隆过滤器中，
 * 否则查询已存在的场馆时也会被布隆过滤器拦截（误判为不存在）。
 */
@Slf4j
@Component
public class VenueBloomDataLoader implements ApplicationRunner {

    @Autowired
    private IVenueService venueService;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始场馆布隆过滤器预热...");
        RBloomFilter<String> bloomFilter = cacheClient.getBloomFilter(BLOOM_VENUE_KEY);

        List<Venue> venues = venueService.list();
        if (venues == null || venues.isEmpty()) {
            log.info("暂无场馆数据，布隆过滤器预热结束");
            return;
        }

        for (Venue venue : venues) {
            bloomFilter.add(venue.getId().toString());
        }
        log.info("场馆布隆过滤器预热完成，共加载{}个场馆ID", venues.size());
    }
}
