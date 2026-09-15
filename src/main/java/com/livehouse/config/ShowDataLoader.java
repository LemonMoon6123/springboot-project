package com.livehouse.config;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.livehouse.entity.Show;
import com.livehouse.service.IShowService;
import com.livehouse.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.livehouse.utils.RedisConstants.CACHE_SHOW_KEY;

/**
 * @Auther: shaolei
 * @Date: 2026/9/14-16:07
 * @Description：项目启动时加载redis热点数据（演出信息），否则热点数据无法被命中（利用逻辑过期），查询不到演出信息。
 */
@Slf4j
@Component
public class ShowDataLoader implements ApplicationRunner {

    @Autowired
    private IShowService showService;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始演出数据预热...");
        // 1. 查询未来和现在进行中的演出信息
        LocalDateTime now = LocalDateTime.now();
        List<Show> shows = showService.listActiveShows(now);

        if (shows == null || shows.isEmpty()) {
            log.info("暂无符合条件的演出数据，缓存预热结束");
            return;
        }

        try {
            int count = 0;
            for (Show show : shows) {
                // 2. 封装成RedisData对象
                RedisData redisData = new RedisData();
                redisData.setData(show);
                redisData.setExpireTime(LocalDateTime.now());

                // 3. 设置cahcheKey
                String key = CACHE_SHOW_KEY + show.getId();

                // 4. 写入redis缓存中
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
                count++;
            }
            log.info("已完成演出数据预热，共加载{}条数据：", count);
        }catch (Exception e){
            log.info("演出数据预热失败...");
        }
    }
}
