package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Favorite;
import com.livehouse.mapper.AppSearchMapper;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.service.app.AppSearchService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppSearchServiceImpl implements AppSearchService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AppSearchMapper appSearchMapper;
    @Resource
    private FavoriteMapper favoriteMapper;

    @Override
    public Map<String, Object> search(String keyword, String city, String time, Long artistId, String price,
                                      Integer hasTicket, String sort, int current, int size, Long userId) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (sort == null || sort.isEmpty()) {
            sort = "hot";
        }
        LocalDateTime timeStart = null;
        LocalDateTime timeEnd = null;
        if (time != null && !time.isEmpty()) {
            LocalDate today = LocalDate.now();
            switch (time) {
                case "today":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusDays(1).atStartOfDay();
                    break;
                case "weekend":
                    LocalDate sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                    timeStart = sat.atStartOfDay();
                    timeEnd = sat.plusDays(2).atStartOfDay();
                    break;
                case "month":
                    timeStart = today.withDayOfMonth(1).atStartOfDay();
                    timeEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                    break;
                case "m1":
                    timeStart = LocalDateTime.now();
                    timeEnd = timeStart.plusMonths(1);
                    break;
                case "m3":
                    timeStart = LocalDateTime.now();
                    timeEnd = timeStart.plusMonths(3);
                    break;
                default:
                    break;
            }
        }

        BigDecimal priceMin = null;
        BigDecimal priceMax = null;
        if (price != null && !price.isEmpty()) {
            String band = price.split(",")[0].trim();
            String[] parts = band.split("-");
            if (parts.length >= 1 && !parts[0].isEmpty()) {
                priceMin = new BigDecimal(parts[0]);
            }
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                priceMax = new BigDecimal(parts[1]);
            }
        }

        long showTotal = appSearchMapper.countSearchShows(keyword, city, artistId, timeStart, timeEnd,
                priceMin, priceMax, hasTicket);
        long offset = (long) (current - 1) * size;
        List<Map<String, Object>> showRows = appSearchMapper.selectSearchShows(
                keyword, city, artistId, timeStart, timeEnd, priceMin, priceMax, hasTicket, sort, offset, size);
        List<Map<String, Object>> showRecords = showRows.stream().map(this::toShowRecord).collect(Collectors.toList());

        long artistTotal = appSearchMapper.countSearchArtists(keyword);
        List<Map<String, Object>> artistRows = appSearchMapper.selectSearchArtists(keyword, 0, 10);
        Set<Long> followedArtists = userId == null ? Collections.emptySet() : favoriteIds(userId, "artist");
        List<Map<String, Object>> artistRecords = artistRows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Long id = ((Number) row.get("id")).longValue();
            m.put("id", String.valueOf(id));
            m.put("name", row.get("name"));
            m.put("avatar", row.get("avatar"));
            String type = row.get("type") == null ? "" : row.get("type").toString();
            String acity = row.get("city") == null ? "" : row.get("city").toString();
            m.put("desc", type + " · " + acity);
            m.put("followed", followedArtists.contains(id));
            return m;
        }).collect(Collectors.toList());

        long venueTotal = appSearchMapper.countSearchVenues(keyword, city);
        List<Map<String, Object>> venueRows = appSearchMapper.selectSearchVenues(keyword, city, 0, 10);
        Set<Long> followedVenues = userId == null ? Collections.emptySet() : favoriteIds(userId, "venue");
        List<Map<String, Object>> venueRecords = venueRows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Long id = ((Number) row.get("id")).longValue();
            m.put("id", String.valueOf(id));
            m.put("name", row.get("name"));
            m.put("city", row.get("city"));
            m.put("address", row.get("address"));
            m.put("followed", followedVenues.contains(id));
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> top3 = appSearchMapper.selectWantRankTop3();
        List<Map<String, Object>> wantTop = top3.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("performanceId", String.valueOf(r.get("performanceId")));
            m.put("title", r.get("title"));
            m.put("wantCount", r.get("wantCount"));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shows", page(showRecords, showTotal, current, size));
        data.put("artists", page(artistRecords, artistTotal, 1, artistRecords.size()));
        data.put("venues", page(venueRecords, venueTotal, 1, venueRecords.size()));
        Map<String, Object> wantRank = new LinkedHashMap<>();
        wantRank.put("title", "Livehouse 想看榜");
        wantRank.put("top3", wantTop);
        data.put("wantRank", wantRank);
        data.put("keyword", keyword == null ? "" : keyword);
        return data;
    }

    private Set<Long> favoriteIds(Long userId, String targetType) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetType, targetType))
                .stream().map(Favorite::getTargetId).collect(Collectors.toSet());
    }

    private static Map<String, Object> page(List<Map<String, Object>> records, long total, int current, int size) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("records", records);
        p.put("total", total);
        p.put("current", current);
        p.put("size", size);
        p.put("pages", total == 0 ? 0 : (total + size - 1) / size);
        return p;
    }

    private Map<String, Object> toShowRecord(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("showId", String.valueOf(row.get("showId")));
        r.put("performanceId", String.valueOf(row.get("performanceId")));
        r.put("title", row.get("title"));
        r.put("artist", row.get("artist"));
        r.put("cover", row.get("cover"));
        r.put("posterTag", row.get("posterTag"));
        Object st = row.get("startTime");
        if (st instanceof LocalDateTime) {
            r.put("startTime", ((LocalDateTime) st).format(DT));
        } else {
            r.put("startTime", st);
        }
        r.put("venueName", row.get("venueName"));
        r.put("city", row.get("city"));
        Object mp = row.get("minPrice");
        BigDecimal minPrice = mp instanceof BigDecimal ? (BigDecimal) mp : null;
        r.put("minPrice", minPrice);
        r.put("priceText", minPrice == null ? "" : "¥" + minPrice.stripTrailingZeros().toPlainString() + " 起");
        r.put("status", row.get("status"));
        r.put("statusText", "售票中");
        r.put("isRealName", row.get("isRealName"));
        r.put("wantCount", row.get("wantCount"));
        String tag = row.get("posterTag") == null ? null : row.get("posterTag").toString();
        r.put("cardType", tag != null && !tag.isEmpty() ? "big" : "small");
        return r;
    }
}