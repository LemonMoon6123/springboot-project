package com.livehouse.service.app;

import com.livehouse.vo.app.search.HotArtistVo;
import com.livehouse.vo.app.search.SearchResultVo;

import java.util.List;

public interface AppSearchService {

    SearchResultVo search(String keyword, String city, String time, Long artistId, String price,
                          Integer hasTicket, String sort, int current, int size, Long userId);

    List<HotArtistVo> hotArtists(int limit);
}