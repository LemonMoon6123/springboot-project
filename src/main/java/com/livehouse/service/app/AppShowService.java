package com.livehouse.service.app;

import com.livehouse.vo.app.show.HomeVo;
import com.livehouse.vo.app.show.ShowDetailVo;
import com.livehouse.vo.app.show.ShowListVo;

public interface AppShowService {

    HomeVo home(String city, String type, int current, int size);

    ShowListVo list(String city, String type, String date, String sort,
                    Long venueId, Long artistId, int current, int size, Long userId);

    ShowDetailVo detail(Long performanceId, Long showId, boolean noticesAll, Long userId);
}