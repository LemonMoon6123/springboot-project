package com.hmdp.entity.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.entity.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    // 对于查询店铺这个接口，专门做redis缓存处理，以应对一些突发情况。
    public Result queryById(Long id) {
        Shop shop = cacheClient
                .queryWithLogicExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("未查到该店铺信息，该店铺不存在！");
        }
        return Result.ok(shop);
    }

    @Transactional
    public Result update(Shop shop) {
        // 1.先判断店铺是否存在。
        Long id = shop.getId();
        if(id == null){
            return Result.fail("该店铺不存在！");
        }
        // 2.先更新数据库。
        updateById(shop);
        // 3.再删除Redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id); // 可能会在此时遇到一个线程读到的是旧数据，然后填充的缓存数据是旧的。
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.是否需要根据坐标查询（坐标前端不一定传过来）
        if(x == null || y == null){
            // 不需要，查数据库信息并返回。
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int start = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;

        // 3.查询redis,按照距离排序、分页  // GEOSEARCH key BYLONL radius withdistence acs/desc
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000), // 默认单位为米
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) // limit限制搜索条数
        );

        if(results == null){
            return Result.ok(Collections.emptyList());
        }

        // 4.解析出ids
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= start){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> map = new HashMap<>(list.size());
        // 4.1 截取start - end的部分
        list.stream().skip(start).forEach(result -> {
            // 4.2 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));

            // 4.3 获取对应距离
            Distance distance = result.getDistance();
            map.put(shopIdStr,distance);
        });
        String idStr = StrUtil.join(",", ids);
        // 5.根据id查询出shop
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
