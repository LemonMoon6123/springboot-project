package com.livehouse.config;

import com.livehouse.entity.TicketType;
import com.livehouse.service.ITicketTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.livehouse.utils.RedisConstants.TICKET_STOCK_KEY;

/**
 * 票种库存预热
 * 项目启动时加载所有售票中的票种库存到Redis
 */
@Slf4j
@Component
public class TicketStockLoader implements ApplicationRunner {

    @Autowired
    private ITicketTypeService ticketTypeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始预热票种库存数据到Redis...");
        
        try {
            // 1. 查询所有在售的票种
//            List<TicketType> ticketTypes = ticketTypeService.query()
//                    .le("sale_start_time", LocalDateTime.now()) // 开售时间已到
//                    .and(wrapper -> wrapper.isNull("sale_end_time") // 无结束时间
//                            .or()
//                            .gt("sale_end_time", LocalDateTime.now())) // 或未到结束时间
//                    .gt("left_stock", 0) // 有剩余库存
//                    .list();

            List<TicketType> ticketTypes = ticketTypeService.query().gt("left_stock", 0) // 有剩余库存
                    .list();

            if (ticketTypes.isEmpty()) {
                log.warn("没有找到在售票种数据，跳过库存预热");
                return;
            }

            // 2. 将库存信息加载到Redis
            int loadedCount = 0;
            for (TicketType ticketType : ticketTypes) {
                String stockKey = TICKET_STOCK_KEY + ticketType.getId();
                
                // 设置库存数量
                stringRedisTemplate.opsForValue().set(stockKey, ticketType.getLeftStock().toString());
                
                loadedCount++;
                
                log.debug("已加载票种 {} 的库存：{}", ticketType.getId(), ticketType.getLeftStock());
            }

            log.info("票种库存预热完成，共加载 {} 个票种的库存数据", loadedCount);
            
        } catch (Exception e) {
            log.error("票种库存预热失败", e);
        }
    }

    /**
     * 手动重新加载指定票种的库存
     * @param ticketTypeId 票种ID
     */
    public void reloadStock(Long ticketTypeId) {
        try {
            TicketType ticketType = ticketTypeService.getById(ticketTypeId);
            if (ticketType != null) {
                String stockKey = TICKET_STOCK_KEY + ticketTypeId;
                stringRedisTemplate.opsForValue().set(stockKey, ticketType.getLeftStock().toString());
                log.info("已重新加载票种 {} 的库存：{}", ticketTypeId, ticketType.getLeftStock());
            }
        } catch (Exception e) {
            log.error("重新加载票种 {} 库存失败", ticketTypeId, e);
        }
    }

    /**
     * 清除指定票种的库存缓存
     * @param ticketTypeId 票种ID
     */
    public void clearStock(Long ticketTypeId) {
        try {
            String stockKey = TICKET_STOCK_KEY + ticketTypeId;
            stringRedisTemplate.delete(stockKey);
            log.info("已清除票种 {} 的库存缓存", ticketTypeId);
        } catch (Exception e) {
            log.error("清除票种 {} 库存缓存失败", ticketTypeId, e);
        }
    }
}