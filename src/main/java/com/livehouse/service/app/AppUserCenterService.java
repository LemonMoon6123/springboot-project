package com.livehouse.service.app;

import com.livehouse.vo.app.user.UserCenterVo;

public interface AppUserCenterService {

    UserCenterVo center(Long userId);
}