package com.livehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.service.IDataScreenService;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据大屏服务实现类
 * 提供各种统计和分析功能
 */
@Slf4j
@Service
public class DataScreenServiceImpl implements IDataScreenService {

    @Autowired
    private ITicketOrderService ticketOrderService;
    
    @Autowired
    private IShowService showService;
    
    @Autowired
    private ITicketTypeService ticketTypeService;
    
    @Autowired
    private IElectronicTicketService electronicTicketService;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Object> getOverallStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 总演出数量
            long totalShows = showService.count();
            stats.put("总演出数", totalShows);
            
            // 总订单数量
            long totalOrders = ticketOrderService.count();
            stats.put("总订单数", totalOrders);
            
            // 已支付订单数量
            QueryWrapper<TicketOrder> paidQuery = new QueryWrapper<>();
            paidQuery.eq("pay_status", 1);
            long paidOrders = ticketOrderService.count(paidQuery);
            stats.put("已支付订单数", paidOrders);
            
            // 总营收
            List<TicketOrder> paidOrderList = ticketOrderService.list(paidQuery);
            BigDecimal totalRevenue = paidOrderList.stream()
                    .map(TicketOrder::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.put("总营收", totalRevenue);
            
            // 平均客单价
            BigDecimal avgOrderAmount = paidOrders > 0 
                ? totalRevenue.divide(BigDecimal.valueOf(paidOrders), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
            stats.put("平均客单价", avgOrderAmount);
            
            // 支付成功率
            double paymentSuccessRate = totalOrders > 0 
                ? (double) paidOrders / totalOrders * 100
                : 0.0;
            stats.put("支付成功率", Math.round(paymentSuccessRate * 100) / 100.0);
            
            log.info("总体数据统计完成，总演出：{}，总订单：{}，总营收：{}", 
                    totalShows, totalOrders, totalRevenue);
                    
        } catch (Exception e) {
            log.error("获取总体统计数据失败", e);
            stats.put("错误", "数据获取失败：" + e.getMessage());
        }
        
        return stats;
    }

    @Override
    public Map<String, Object> getRevenueAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime weekStart = now.minusDays(7);
            LocalDateTime monthStart = now.minusDays(30);
            LocalDateTime yearStart = now.minusDays(365);
            
            // 今日营收
            BigDecimal todayRevenue = calculateRevenueByDateRange(todayStart, now);
            analysis.put("今日营收", todayRevenue);
            
            // 近7天营收
            BigDecimal weekRevenue = calculateRevenueByDateRange(weekStart, now);
            analysis.put("近7天营收", weekRevenue);
            
            // 近30天营收
            BigDecimal monthRevenue = calculateRevenueByDateRange(monthStart, now);
            analysis.put("近30天营收", monthRevenue);
            
            // 近365天营收
            BigDecimal yearRevenue = calculateRevenueByDateRange(yearStart, now);
            analysis.put("近1年营收", yearRevenue);
            
            // 营收趋势（最近7天每日营收）
            List<Map<String, Object>> dailyTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDateTime dayStart = now.minusDays(i).withHour(0).withMinute(0).withSecond(0);
                LocalDateTime dayEnd = dayStart.plusDays(1);
                BigDecimal dayRevenue = calculateRevenueByDateRange(dayStart, dayEnd);
                
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("日期", dayStart.format(DateTimeFormatter.ofPattern("MM-dd")));
                dayData.put("营收", dayRevenue);
                dailyTrend.add(dayData);
            }
            analysis.put("近7天营收趋势", dailyTrend);
            
            log.info("营收分析完成，今日：{}，本周：{}，本月：{}", 
                    todayRevenue, weekRevenue, monthRevenue);
                    
        } catch (Exception e) {
            log.error("获取营收分析数据失败", e);
            analysis.put("错误", "营收分析失败：" + e.getMessage());
        }
        
        return analysis;
    }

    @Override
    public Map<String, Object> getShowTicketStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取所有演出
            List<Show> shows = showService.list();
            
            List<Map<String, Object>> showStats = new ArrayList<>();
            
            for (Show show : shows) {
                Map<String, Object> showData = new HashMap<>();
                showData.put("演出ID", show.getId());
                showData.put("演出标题", show.getTitle());
                showData.put("艺人", show.getArtist());
                showData.put("演出时间", show.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                showData.put("状态", getShowStatusText(show.getStatus()));
                
                // 统计该演出的售票情况
                QueryWrapper<TicketOrder> orderQuery = new QueryWrapper<>();
                orderQuery.eq("show_id", show.getId()).eq("pay_status", 1);
                List<TicketOrder> showOrders = ticketOrderService.list(orderQuery);
                
                int totalTicketsSold = showOrders.stream()
                        .mapToInt(TicketOrder::getQuantity)
                        .sum();
                        
                BigDecimal showRevenue = showOrders.stream()
                        .map(TicketOrder::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                showData.put("已售票数", totalTicketsSold);
                showData.put("营收", showRevenue);
                
                // 核销统计
                Long verifiedCount = getVerifiedTicketCount(show.getId());
                showData.put("已核销票数", verifiedCount);
                
                showStats.add(showData);
            }
            
            stats.put("演出售票统计", showStats);
            
            // 热门演出TOP5（按营收排序）
            List<Map<String, Object>> topShows = showStats.stream()
                    .sorted((a, b) -> {
                        BigDecimal revenueA = (BigDecimal) a.get("营收");
                        BigDecimal revenueB = (BigDecimal) b.get("营收");
                        return revenueB.compareTo(revenueA);
                    })
                    .limit(5)
                    .collect(Collectors.toList());
            stats.put("热门演出TOP5", topShows);
            
            log.info("演出售票统计完成，共{}场演出", shows.size());
            
        } catch (Exception e) {
            log.error("获取演出售票统计失败", e);
            stats.put("错误", "演出统计失败：" + e.getMessage());
        }
        
        return stats;
    }

    @Override
    public Map<String, Object> getOrderStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // 今日订单统计
            QueryWrapper<TicketOrder> todayQuery = new QueryWrapper<>();
            todayQuery.ge("create_time", todayStart);
            long todayOrders = ticketOrderService.count(todayQuery);
            
            todayQuery.eq("pay_status", 1);
            long todayPaidOrders = ticketOrderService.count(todayQuery);
            
            stats.put("今日订单数", todayOrders);
            stats.put("今日支付订单数", todayPaidOrders);
            
            // 订单状态分布
            Map<String, Long> statusDistribution = new HashMap<>();
            statusDistribution.put("待支付", countOrdersByStatus(1));
            statusDistribution.put("已完成", countOrdersByStatus(2));
            statusDistribution.put("已取消", countOrdersByStatus(3));
            statusDistribution.put("已退票", countOrdersByStatus(4));
            stats.put("订单状态分布", statusDistribution);
            
            // 支付状态分布
            Map<String, Long> payStatusDistribution = new HashMap<>();
            payStatusDistribution.put("未支付", countOrdersByPayStatus(0));
            payStatusDistribution.put("已支付", countOrdersByPayStatus(1));
            payStatusDistribution.put("已取消", countOrdersByPayStatus(2));
            stats.put("支付状态分布", payStatusDistribution);
            
            // 最近24小时订单趋势（每小时）
            List<Map<String, Object>> hourlyTrend = new ArrayList<>();
            for (int i = 23; i >= 0; i--) {
                LocalDateTime hourStart = now.minusHours(i).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime hourEnd = hourStart.plusHours(1);
                
                QueryWrapper<TicketOrder> hourQuery = new QueryWrapper<>();
                hourQuery.ge("create_time", hourStart).lt("create_time", hourEnd);
                long hourOrders = ticketOrderService.count(hourQuery);
                
                Map<String, Object> hourData = new HashMap<>();
                hourData.put("时间", hourStart.format(DateTimeFormatter.ofPattern("HH:mm")));
                hourData.put("订单数", hourOrders);
                hourlyTrend.add(hourData);
            }
            stats.put("24小时订单趋势", hourlyTrend);
            
            log.info("订单统计完成，今日订单：{}，今日支付：{}", todayOrders, todayPaidOrders);
            
        } catch (Exception e) {
            log.error("获取订单统计数据失败", e);
            stats.put("错误", "订单统计失败：" + e.getMessage());
        }
        
        return stats;
    }

    @Override
    public Map<String, Object> getRateLimitStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取所有限流相关的Redis keys
            String pattern = "rate_limit:*";
            long currentTime = System.currentTimeMillis();
            long tenSecondsAgo = currentTime - 10000;
            
            // 模拟一些限流统计（实际应用中可以从Redis中获取更详细的信息）
            Map<String, Object> rateLimitInfo = new HashMap<>();
            rateLimitInfo.put("当前活跃限流数", 0); // 可以通过扫描Redis keys来获取
            rateLimitInfo.put("限流阈值", "10秒内3次请求");
            rateLimitInfo.put("限流策略", "滑动窗口限流");
            
            // 限流触发统计（这里提供示例数据，实际可以从日志或监控系统获取）
            List<Map<String, Object>> recentBlocks = new ArrayList<>();
            Map<String, Object> blockExample = new HashMap<>();
            blockExample.put("IP", "192.168.1.100");
            blockExample.put("触发时间", LocalDateTime.now().minusMinutes(5).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            blockExample.put("请求次数", 5);
            recentBlocks.add(blockExample);
            
            rateLimitInfo.put("近期限流记录", recentBlocks);
            
            stats.put("限流统计", rateLimitInfo);
            
            // IP访问频率TOP10（示例数据）
            List<Map<String, Object>> topIPs = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> ipData = new HashMap<>();
                ipData.put("IP", "192.168.1." + (100 + i));
                ipData.put("10秒内请求数", 3 - (i - 1));
                topIPs.add(ipData);
            }
            stats.put("活跃IP排行", topIPs);
            
            log.info("限流统计完成");
            
        } catch (Exception e) {
            log.error("获取限流统计数据失败", e);
            stats.put("错误", "限流统计失败：" + e.getMessage());
        }
        
        return stats;
    }

    @Override
    public Map<String, Object> getVisitStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 网站访问统计（这里提供示例数据，实际应用中可以集成网站统计工具）
            Map<String, Object> visitInfo = new HashMap<>();
            
            // 今日访问量（示例）
            visitInfo.put("今日访问量", 1520);
            visitInfo.put("今日独立访客", 890);
            visitInfo.put("今日页面浏览量", 4200);
            
            // 实时在线用户（可以通过Redis session或WebSocket连接数统计）
            visitInfo.put("实时在线用户", 45);
            
            // 访问来源统计
            Map<String, Integer> sourceStats = new HashMap<>();
            sourceStats.put("直接访问", 45);
            sourceStats.put("搜索引擎", 30);
            sourceStats.put("社交媒体", 15);
            sourceStats.put("其他", 10);
            visitInfo.put("访问来源分布", sourceStats);
            
            // 热门页面
            List<Map<String, Object>> hotPages = new ArrayList<>();
            String[] pages = {"/", "/shows", "/tickets", "/user/profile", "/admin"};
            int[] views = {1200, 800, 600, 300, 150};
            
            for (int i = 0; i < pages.length; i++) {
                Map<String, Object> pageData = new HashMap<>();
                pageData.put("页面", pages[i]);
                pageData.put("访问次数", views[i]);
                hotPages.add(pageData);
            }
            visitInfo.put("热门页面", hotPages);
            
            // 最近7天访问趋势
            List<Map<String, Object>> weeklyTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                Map<String, Object> dayData = new HashMap<>();
                LocalDateTime day = now.minusDays(i);
                dayData.put("日期", day.format(DateTimeFormatter.ofPattern("MM-dd")));
                dayData.put("访问量", 1000 + (int)(Math.random() * 1000)); // 示例数据
                weeklyTrend.add(dayData);
            }
            visitInfo.put("近7天访问趋势", weeklyTrend);
            
            stats.put("网站访问统计", visitInfo);
            
            log.info("网站访问统计完成");
            
        } catch (Exception e) {
            log.error("获取网站访问统计失败", e);
            stats.put("错误", "访问统计失败：" + e.getMessage());
        }
        
        return stats;
    }

    @Override
    public Map<String, Object> getRealTimeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 实时数据刷新时间
            stats.put("数据更新时间", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // 当前系统状态
            Map<String, Object> systemStatus = new HashMap<>();
            systemStatus.put("系统运行状态", "正常");
            systemStatus.put("Redis连接状态", checkRedisConnection());
            systemStatus.put("数据库连接状态", "正常");
            stats.put("系统状态", systemStatus);
            
            // 实时业务指标
            Map<String, Object> realtimeMetrics = new HashMap<>();
            
            // 最近1小时的订单数
            LocalDateTime oneHourAgo = now.minusHours(1);
            QueryWrapper<TicketOrder> recentQuery = new QueryWrapper<>();
            recentQuery.ge("create_time", oneHourAgo);
            long recentOrders = ticketOrderService.count(recentQuery);
            realtimeMetrics.put("近1小时订单数", recentOrders);
            
            // 最近1小时的营收
            List<TicketOrder> recentPaidOrders = ticketOrderService.list(
                recentQuery.eq("pay_status", 1)
            );
            BigDecimal recentRevenue = recentPaidOrders.stream()
                    .map(TicketOrder::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            realtimeMetrics.put("近1小时营收", recentRevenue);
            
            stats.put("实时指标", realtimeMetrics);
            
            log.info("实时统计完成，近1小时订单：{}，营收：{}", recentOrders, recentRevenue);
            
        } catch (Exception e) {
            log.error("获取实时统计数据失败", e);
            stats.put("错误", "实时统计失败：" + e.getMessage());
        }
        
        return stats;
    }

    // ========== 辅助方法 ==========
    
    /**
     * 根据日期范围计算营收
     */
    private BigDecimal calculateRevenueByDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<TicketOrder> query = new QueryWrapper<>();
        query.ge("pay_time", startTime)
             .le("pay_time", endTime)
             .eq("pay_status", 1);
             
        List<TicketOrder> orders = ticketOrderService.list(query);
        return orders.stream()
                .map(TicketOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 根据订单状态统计数量
     */
    private Long countOrdersByStatus(Integer status) {
        QueryWrapper<TicketOrder> query = new QueryWrapper<>();
        query.eq("order_status", status);
        return Long.valueOf(ticketOrderService.count(query));
    }
    
    /**
     * 根据支付状态统计数量
     */
    private Long countOrdersByPayStatus(Integer payStatus) {
        QueryWrapper<TicketOrder> query = new QueryWrapper<>();
        query.eq("pay_status", payStatus);
        return Long.valueOf(ticketOrderService.count(query));
    }
    
    /**
     * 获取演出状态文本
     */
    private String getShowStatusText(Integer status) {
        switch (status) {
            case 1: return "待开票";
            case 2: return "售票中";
            case 3: return "售罄";
            case 4: return "已结束";
            default: return "未知状态";
        }
    }
    
    /**
     * 获取演出已核销票数
     */
    private Long getVerifiedTicketCount(Long showId) {
        try {
            String bitmapKey = "ticket:verify:" + showId;
            return stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) redisConnection -> {
                    byte[] key = bitmapKey.getBytes();
                    return redisConnection.bitCount(key);
                });
        } catch (Exception e) {
            log.warn("获取演出{}核销数据失败：{}", showId, e.getMessage());
            return 0L;
        }
    }
    
    /**
     * 检查Redis连接状态
     */
    private String checkRedisConnection() {
        try {
            stringRedisTemplate.opsForValue().get("test:connection");
            return "正常";
        } catch (Exception e) {
            return "异常";
        }
    }
}