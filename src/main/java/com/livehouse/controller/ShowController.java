package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.entity.Show;
import com.livehouse.service.IShowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 演出控制器
 */
@RestController
@RequestMapping("/show")
public class ShowController {

    @Resource
    private IShowService showService;

    /**
     * 根据ID查询演出详情
     * @param id 演出ID
     * @return 演出详情数据
     */
    @GetMapping("/{id}")
    public Result queryShowById(@PathVariable("id") Long id) {
        return showService.queryById(id);
    }

    /**
     * 新增演出信息
     * @param show 演出数据
     * @return 演出ID
     */
    @PostMapping
    public Result saveShow(@RequestBody Show show) {
        // 写入数据库
        showService.save(show);
        // 返回演出ID
        return Result.ok(show.getId());
    }

    /**
     * 更新演出信息
     * @param show 演出数据
     * @return 无
     */
    @PutMapping
    public Result updateShow(@RequestBody Show show) {
        return showService.update(show);
    }

    /**
     * 根据城市分页查询演出信息
     * @param city 城市名称
     * @param current 页码
     * @param x 经度（可选）
     * @param y 纬度（可选）
     * @return 演出列表
     */
    @GetMapping("/of/city")
    public Result queryShowByCity(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return showService.queryShowByCity(city, current, x, y);
    }

    /**
     * 根据演出名称关键字分页查询演出信息
     * @param name 演出名称关键字
     * @param current 页码
     * @return 演出列表
     */
    @GetMapping("/of/name")
    public Result queryShowByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return showService.queryShowByName(name, current);
    }

    /**
     * 获取热门演出列表
     * @param current 页码
     * @return 热门演出列表
     */
    @GetMapping("/hot")
    public Result queryHotShows(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return showService.queryShowByCity(null, current, null, null);
    }
}