package com.livehouse.config;

import com.livehouse.entity.Venue;
import com.livehouse.service.IVenueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.livehouse.utils.RedisConstants.VENUE_GEO_KEY;

/**
 * Redis GEO数据预热
 * 项目启动时加载所有场馆的地理位置信息到Redis
 */
@Slf4j
@Component
public class RedisGeoDataLoader implements ApplicationRunner {

    @Autowired
    private IVenueService venueService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始预热场馆地理位置数据到Redis...");
        
        try {
            // 1. 查询所有场馆
            List<Venue> venues = venueService.list();
            
            if (venues.isEmpty()) {
                log.warn("没有找到场馆数据，跳过GEO数据预热");
                return;
            }

            // 2. 按城市分组
            Map<String, List<Venue>> venuesByCity = venues.stream()
                    .collect(Collectors.groupingBy(Venue::getCity));

            // 3. 为每个城市加载GEO数据
            for (Map.Entry<String, List<Venue>> entry : venuesByCity.entrySet()) {
                String city = entry.getKey();
                List<Venue> cityVenues = entry.getValue();
                
                loadGeoDataForCity(city, cityVenues);
            }
            
            log.info("场馆地理位置数据预热完成，共处理{}个城市，{}个场馆", 
                    venuesByCity.size(), venues.size());
                    
        } catch (Exception e) {
            log.error("场馆地理位置数据预热失败", e);
        }
    }

    /**
     * 为指定城市加载GEO数据
     */
    private void loadGeoDataForCity(String city, List<Venue> venues) {
        String key = VENUE_GEO_KEY + city;
        
        // 清除旧数据
        stringRedisTemplate.delete(key);
        
        // 准备GEO数据
        List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>();
        
        for (Venue venue : venues) {
            if (venue.getLongitude() != null && venue.getLatitude() != null) {
                Point point = new Point(venue.getLongitude(), venue.getLatitude());
                RedisGeoCommands.GeoLocation<String> geoLocation = 
                    new RedisGeoCommands.GeoLocation<>(venue.getId().toString(), point);
                geoLocations.add(geoLocation);
            }
        }
        
        if (!geoLocations.isEmpty()) {
            // 批量添加到Redis GEO
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
            log.info("已为城市 {} 加载 {} 个场馆的地理位置数据", city, geoLocations.size());
        }
    }
}