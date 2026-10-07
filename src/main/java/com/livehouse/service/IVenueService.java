package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.entity.Venue;

/**
 * 场馆服务接口
 */
public interface IVenueService extends IService<Venue> {

    /**
     * 根据ID查询场馆（布隆过滤器防穿透 + 读写锁保证双写一致）
     */
    Venue queryVenueById(Long id);

    /**
     * 更新场馆（写锁内更新数据库并删除缓存，保证双写一致）
     */
    boolean updateVenue(Venue venue);

    /**
     * 新增场馆（写库后将ID加入布隆过滤器）
     */
    boolean saveVenue(Venue venue);
}