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
        if ("new".equals(sort)) {
            sort = "update";
        }
        // 根据time字段设置起止时间。
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
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(1).atStartOfDay();
                    break;
                case "m3":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(3).atStartOfDay();
                    break;
                case "本周末":
                    LocalDate satZh = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                    timeStart = satZh.atStartOfDay();
                    timeEnd = satZh.plusDays(2).atStartOfDay();
                    break;
                case "今天":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusDays(1).atStartOfDay();
                    break;
                case "本月":
                    timeStart = today.withDayOfMonth(1).atStartOfDay();
                    timeEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                    break;
                case "一个月内":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(1).atStartOfDay();
                    break;
                case "三个月内":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(3).atStartOfDay();
                    break;
                default:
                    break;
            }
        }

        BigDecimal[] band = parsePriceBand(price);
        BigDecimal priceMin = band[0];
        BigDecimal priceMax = band[1];


        // 统计搜索出的演出数量
        long showTotal = appSearchMapper.countSearchShows(keyword, city, artistId, timeStart, timeEnd,
                priceMin, priceMax, hasTicket);
        // 计算每页起始索引
        long offset = (long) (current - 1) * size;
        List<Map<String, Object>> showRows = appSearchMapper.selectSearchShows(
                keyword, city, artistId, timeStart, timeEnd, priceMin, priceMax, hasTicket, sort, offset, size);
        // 将查询结果做些简单处理
        List<Map<String, Object>> showRecords = showRows.stream().map(this::toShowRecord).collect(Collectors.toList());

        // 查询模糊查询艺人信息条数
        long artistTotal = appSearchMapper.countSearchArtists(keyword);
        // 模糊查询艺人信息
        List<Map<String, Object>> artistRows = appSearchMapper.selectSearchArtists(keyword, 0, 10);
        List<Map<String, Object>> pickerRows = appSearchMapper.selectSearchArtists(null, 0, 80);
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
        List<Map<String, Object>> artistPicker = pickerRows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Long id = ((Number) row.get("id")).longValue();
            m.put("id", String.valueOf(id));
            m.put("name", row.get("name"));
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
        data.put("artistPicker", artistPicker);
        data.put("venues", page(venueRecords, venueTotal, 1, venueRecords.size()));
        Map<String, Object> wantRank = new LinkedHashMap<>();
        wantRank.put("title", "Livehouse 想看榜");
        wantRank.put("top3", wantTop);
        data.put("wantRank", wantRank);
        data.put("keyword", keyword == null ? "" : keyword);
        return data;
    }

  /** 解析前端价格筛选项（如「100 以内」「100~300」） */
    private static BigDecimal[] parsePriceBand(String price) {
        if (price == null || price.isEmpty()) {
            return new BigDecimal[]{null, null};
        }
        String band = price.split(",")[0].trim();
        switch (band) {
            case "100 以内":
                return new BigDecimal[]{null, new BigDecimal("100")};
            case "100~300":
                return new BigDecimal[]{new BigDecimal("100"), new BigDecimal("300")};
            case "300~500":
                return new BigDecimal[]{new BigDecimal("300"), new BigDecimal("500")};
            case "500 以上":
                return new BigDecimal[]{new BigDecimal("500"), null};
            default:
                break;
        }
        String normalized = band.replace('~', '-').replaceAll("[^0-9\\-.]", "");
        String[] parts = normalized.split("-");
        BigDecimal min = null;
        BigDecimal max = null;
        try {
            if (parts.length >= 1 && !parts[0].isEmpty()) {
                min = new BigDecimal(parts[0]);
            }
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                max = new BigDecimal(parts[1]);
            }
        } catch (NumberFormatException ignored) {
            return new BigDecimal[]{null, null};
        }
        return new BigDecimal[]{min, max};
    }

    private Set<Long> favoriteIds(Long userId, String targetType) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetType, targetType))
                // 只把 targetId 抽出来，即喜欢的艺人id
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

    // 将搜索数据做类型转换、格式化、补充计算处理
    private Map<String, Object> toShowRecord(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("showId", String.valueOf(row.get("showId")));
        r.put("performanceId", String.valueOf(row.get("performanceId")));
        r.put("title", row.get("title"));
        r.put("artist", row.get("artist"));
        r.put("cover", row.get("cover"));
        r.put("posterTag", row.get("posterTag"));
        Object st = row.get("startTime");
        LocalDateTime start = st instanceof LocalDateTime ? (LocalDateTime) st : null;
        if (start != null) {
            r.put("startTime", start.format(DT));
            r.put("showTimeText", formatShowText(start));
        } else {
            r.put("startTime", st);
            r.put("showTimeText", st == null ? "" : st.toString());
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

    private static String formatShowText(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        try {
            return t.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA));
        } catch (Exception e) {
            return t.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        }
    }
}