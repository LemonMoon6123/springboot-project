package com.livehouse.service;

import java.util.Map;

/**
 * 数据大屏服务接口
 * 用于统计和分析各种业务数据
 */
public interface IDataScreenService {
    
    /**
     * 获取总体数据概览
     * @return 包含总体统计数据的Map
     */
    Map<String, Object> getOverallStats();
    
    /**
     * 获取营收分析数据
     * @return 包含不同时间段营收数据的Map
     */
    Map<String, Object> getRevenueAnalysis();
    
    /**
     * 获取演出售票统计
     * @return 包含各演出售票情况的Map
     */
    Map<String, Object> getShowTicketStats();
    
    /**
     * 获取订单统计信息
     * @return 包含订单相关统计的Map
     */
    Map<String, Object> getOrderStats();
    
    /**
     * 获取用户限流统计
     * @return 包含用户限流相关数据的Map
     */
    Map<String, Object> getRateLimitStats();
    
    /**
     * 获取网站访问统计
     * @return 包含网站访问相关数据的Map
     */
    Map<String, Object> getVisitStats();
    
    /**
     * 获取实时数据
     * @return 包含实时统计数据的Map
     */
    Map<String, Object> getRealTimeStats();
}