package com.livehouse.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livehouse.dto.Result;
import com.livehouse.dto.ShowDTO;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketType;
import com.livehouse.entity.Venue;
import com.livehouse.mapper.ShowMapper;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.service.IVenueService;
import com.livehouse.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.livehouse.utils.RedisConstants.*;

/**
 * 演出服务实现类
 */
@Service
public class ShowServiceImpl extends ServiceImpl<ShowMapper, Show> implements IShowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private IVenueService venueService;

    @Autowired
    private ITicketTypeService ticketTypeService;

    // 每页显示数量
    private static final int DEFAULT_PAGE_SIZE = 5;

    /**
     * 根据ID查询演出详情（使用逻辑过期缓存策略）
     */
    @Override
    public Result queryById(Long id) {
        // 使用缓存客户端的逻辑过期策略，因为这是热点数据，需要走redis，同时解决缓存击穿的问题。
        Show show = cacheClient.queryWithLogicExpire(
                CACHE_SHOW_KEY, id, Show.class, 
                this::getShowWithDetails, CACHE_SHOW_TTL, TimeUnit.MINUTES);
                
        if (show == null) {
            return Result.fail("未查到该演出信息，该演出不存在！");
        }
        
        return Result.ok(show);
    }

    /**
     * 查询演出详情（包含场馆和票种信息）
     * 这个方法会被缓存客户端调用
     */
    private Show getShowWithDetails(Long id) {
        Show show = getById(id);
        if (show == null) {
            return null;
        }

        // 查询场馆信息
        Venue venue = venueService.getById(show.getVenueId());
        
        // 查询票种信息
        List<TicketType> ticketTypes = ticketTypeService.query()
                .eq("show_id", id)
                .list();

        // TODO这里可以通过设置额外字段或使用DTO来包含场馆和票种信息，为了简化，我们先返回基本的Show信息
        return show;
    }

    /**
     * 更新演出信息（双写一致性策略）
     */
    @Override
    @Transactional
    public Result update(Show show) {
        Long id = show.getId();
        if (id == null) {
            return Result.fail("该演出不存在！");
        }
        
        // 1. 先更新数据库
        updateById(show);

        // 2. 重新设置带逻辑过期的缓存
        cacheClient.setWithLogicExpireTime(
                CACHE_SHOW_KEY + id,
                show,
                CACHE_SHOW_TTL,
                TimeUnit.MINUTES
        );
        
        return Result.ok();
    }

    /**
     * 根据城市查询演出（支持地理位置排序）
     */
    @Override
    public Result queryShowByCity(String city, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要地理位置排序，直接查询数据库
            Page<Show> page;
            if (StrUtil.isNotBlank(city)) {
                // 根据城市查询（需要关联场馆表）
                page = query()
                        .eq("status", 2) // 只查询售票中的演出
                        .orderByDesc("start_time")
                        .page(new Page<>(current, DEFAULT_PAGE_SIZE));
                // 过滤指定城市的演出
                List<Show> filteredShows = page.getRecords().stream()
                        .filter(show -> {
                            Venue venue = venueService.getById(show.getVenueId());
                            return venue != null && city.equals(venue.getCity());
                        })
                        .collect(Collectors.toList());
                page.setRecords(filteredShows);
            } else {
                // 查询所有售票中的演出
                page = query()
                        .eq("status", 2)
                        .orderByDesc("start_time")
                        .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            }
            return Result.ok(page.getRecords());
        }

        // 2. 计算分页参数
        int start = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        // 3. 从Redis GEO查询附近的场馆
        String key = VENUE_GEO_KEY + (StrUtil.isNotBlank(city) ? city : "all");
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(10000), // 10公里范围内
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .limit(end)
        );

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 解析场馆IDs和距离
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= start) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> venueIds = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        
        list.stream().skip(start).forEach(result -> {
            String venueIdStr = result.getContent().getName();
            venueIds.add(Long.valueOf(venueIdStr));
            distanceMap.put(venueIdStr, result.getDistance());
        });

        // 5. 根据场馆ID查询演出
        if (venueIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Show> shows = query()
                .in("venue_id", venueIds)
                .eq("status", 2) // 售票中
                .orderByAsc("start_time") // 按时间排序
                .list();

        // 6. 构建带距离信息的演出DTO
        List<ShowDTO> showDTOs = shows.stream().map(show -> {
            ShowDTO dto = new ShowDTO();
            dto.setId(show.getId());
            dto.setArtist(show.getArtist());
            dto.setTitle(show.getTitle());
            dto.setType(show.getType());
            dto.setStartTime(show.getStartTime());
            dto.setEndTime(show.getEndTime());
            dto.setStatus(show.getStatus());
            dto.setDescription(show.getDescription());
            dto.setImage(show.getImage());
            
            // 查询场馆信息
            Venue venue = venueService.getById(show.getVenueId());
            dto.setVenue(venue);
            
            // 设置距离
            Distance distance = distanceMap.get(show.getVenueId().toString());
            if (distance != null) {
                dto.setDistance(distance.getValue());
            }
            
            return dto;
        }).sorted(Comparator.comparing(ShowDTO::getDistance, Comparator.nullsLast(Comparator.naturalOrder())))
          .collect(Collectors.toList());

        return Result.ok(showDTOs);
    }

    /**
     * 根据演出名称关键字查询
     */
    @Override
    public Result queryShowByName(String name, Integer current) {
        Page<Show> page = query()
                .like(StrUtil.isNotBlank(name), "title", name)
                .or()
                .like(StrUtil.isNotBlank(name), "artist", name)
                .eq("status", 2) // 只查询售票中的演出
                .orderByDesc("start_time")
                .page(new Page<>(current, DEFAULT_PAGE_SIZE));
                
        return Result.ok(page.getRecords());
    }
}