package com.livehouse.service.app;

import com.livehouse.vo.app.points.PointsExchangeVo;
import com.livehouse.vo.app.points.PointsGoodsVo;
import com.livehouse.vo.app.points.PointsLogListVo;

import java.util.List;

public interface AppPointsService {

    List<PointsGoodsVo> shop(Long userId);

    PointsLogListVo logs(Long userId, int current, int size);

    PointsExchangeVo exchange(Long userId, Long goodsId, String idempotentKey);
}