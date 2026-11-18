package com.livehouse.service.app.credit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class CreditScheduledTask {

    @Resource
    private CreditAccountService creditAccountService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 30_000)
    public void releaseAndExpire() {
        try {
            creditAccountService.releaseDueCredits();
            creditAccountService.expireCredits();
        } catch (Exception e) {
            log.error("积分定时任务失败", e);
        }
    }
}