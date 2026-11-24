package com.livehouse.service.task;

import com.livehouse.service.app.FlashSaleBannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 首页「限时抢票」入口：刷新距开售 ≤24h 的票种（同开售时间取最低价），写入 Redis。
 */
@Slf4j
@Component
public class FlashSaleRefreshScheduledTask implements ApplicationRunner {

    @Resource
    private FlashSaleBannerService flashSaleBannerService;

    @Override
    public void run(ApplicationArguments args) {
        refresh("startup");
    }

    @Scheduled(fixedRate = 5 * 1000, initialDelay = 5 * 1000)
    public void scheduledRefresh() {
        refresh("scheduled");
    }

    private void refresh(String trigger) {
        log.debug("开始刷新限时抢票入口（{}）", trigger);
        try {
            flashSaleBannerService.refresh();
        } catch (Exception e) {
            log.error("限时抢票入口刷新失败", e);
        }
    }
}