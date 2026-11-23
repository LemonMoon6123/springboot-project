package com.livehouse.service.app;

import com.livehouse.vo.app.common.AppPageVo;
import com.livehouse.vo.app.favorite.FavoriteActionVo;
import com.livehouse.vo.app.favorite.FavoriteRecordVo;

public interface AppFavoriteService {

    FavoriteActionVo add(Long userId, String targetType, Long targetId);

    FavoriteActionVo remove(Long userId, String targetType, Long targetId);

    AppPageVo<FavoriteRecordVo> list(Long userId, String type, int current, int size);
}