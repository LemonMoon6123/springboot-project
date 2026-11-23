package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.livehouse.vo.app.show.FlashSaleCandidateVo;

import java.util.List;

@Mapper
public interface AppFlashSaleMapper {

    /**
     * 距开售剩余 ≤24 小时且尚未开售的上架票种（sale_start &gt; now），附带演出/场次信息。
     */
    List<FlashSaleCandidateVo> selectUpcomingSaleCandidates();
}