package com.livehouse.service.app;

import com.livehouse.vo.app.member.MemberInfoVo;

public interface AppMemberService {

    MemberInfoVo memberInfo(Long userId);
}