package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.Result;
import com.livehouse.entity.Show;

/**
 * 演出服务接口
 */
public interface IShowService extends IService<Show> {

    /**
     * 根据ID查询演出详情（包含场馆信息和票种列表）
     * @param id 演出ID
     * @return 演出详情
     */
    Result queryById(Long id);

    /**
     * 更新演出信息
     * @param show 演出信息
     * @return 更新结果
     */
    Result update(Show show);

    /**
     * 根据城市分页查询演出
     * @param city 城市名称
     * @param current 当前页码
     * @param x 经度（可选，用于按距离排序）
     * @param y 纬度（可选，用于按距离排序）
     * @return 演出列表
     */
    Result queryShowByCity(String city, Integer current, Double x, Double y);

    /**
     * 根据演出名称关键字查询
     * @param name 演出名称关键字
     * @param current 当前页码
     * @return 演出列表
     */
    Result queryShowByName(String name, Integer current);
}