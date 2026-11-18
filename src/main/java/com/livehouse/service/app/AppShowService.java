package com.livehouse.service.app;

import java.util.Map;

public interface AppShowService {

    Map<String, Object> home(String city, String type, int current, int size);

    Map<String, Object> list(String city, String type, String date, String sort,
                               Long venueId, Long artistId, int current, int size);

    Map<String, Object> detail(Long performanceId, Long showId, boolean noticesAll, Long userId);
}