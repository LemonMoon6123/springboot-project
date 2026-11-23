package com.livehouse.service.app;

import com.livehouse.vo.app.realname.RealnameVo;

public interface AppRealnameService {

    RealnameVo get(Long userId);

    RealnameVo submit(Long userId, String realName, Integer idType, String idCard, String phone);
}