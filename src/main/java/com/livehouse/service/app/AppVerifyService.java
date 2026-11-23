package com.livehouse.service.app;

import com.livehouse.vo.app.verify.VerifyScanResultVo;
import com.livehouse.vo.app.verify.VerifySessionVo;
import com.livehouse.vo.app.verify.VerifyStatsVo;

import java.util.List;

public interface AppVerifyService {

    List<VerifySessionVo> sessions(Long userId);

    VerifyScanResultVo scan(Long operatorUserId, Long showId, String ticketCode, String shortCode, String gate);

    VerifyStatsVo stats(Long userId, Long showId);
}