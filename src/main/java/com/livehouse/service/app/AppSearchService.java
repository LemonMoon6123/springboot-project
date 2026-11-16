package com.livehouse.service.app;

import java.util.Map;

public interface AppSearchService {

    Map<String, Object> search(String keyword, String city, String time, Long artistId, String price,
                               Integer hasTicket, String sort, int current, int size, Long userId);
}