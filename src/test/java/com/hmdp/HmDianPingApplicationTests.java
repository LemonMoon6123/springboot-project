package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIDGenerater;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedisIDGenerater redisIDGenerater;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testSaveShop(){
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicExpireTime(CACHE_SHOP_KEY + 1,shop, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

    @Test
    public void testGeneraterID() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(() ->{
                for (int j = 0; j < 100; j++) {
                    long id = redisIDGenerater.getId("order");
                    System.out.println("id = " + id);
                }
                latch.countDown();
            });
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testRedisGeo(){
        // 1.从数据库中查询所有店铺信息
        List<Shop> list = shopService.list();
        // 2.根据不同店铺类型，将店铺分组存放在list集合中
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.获取店铺信息
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                geoLocations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            redisTemplate.opsForGeo().add(key,geoLocations);
        }
    }

}
