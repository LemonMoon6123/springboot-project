package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Favorite;
import com.livehouse.mapper.AppSearchMapper;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.service.app.AppSearchService;
import com.livehouse.vo.app.common.AppPageVo;
import com.livehouse.vo.app.convert.SearchVoConverter;
import com.livehouse.vo.app.convert.ShowVoConverter;
import com.livehouse.vo.app.search.SearchArtistRowVo;
import com.livehouse.vo.app.search.HotArtistVo;
import com.livehouse.vo.app.search.SearchArtistItemVo;
import com.livehouse.vo.app.search.SearchArtistPickerVo;
import com.livehouse.vo.app.search.SearchResultVo;
import com.livehouse.vo.app.search.SearchVenueItemVo;
import com.livehouse.vo.app.search.WantRankItemVo;
import com.livehouse.vo.app.search.WantRankVo;
import com.livehouse.vo.app.show.ShowCardVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppSearchServiceImpl implements AppSearchService {

    @Resource
    private AppSearchMapper appSearchMapper;
    @Resource
    private FavoriteMapper favoriteMapper;

    @Override
    public SearchResultVo search(String keyword, String city, String time, Long artistId, String price,
                                 Integer hasTicket, String sort, int current, int size, Long userId) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (sort == null || sort.isEmpty()) {
            sort = "hot";
        }
        if ("new".equals(sort)) {
            sort = "update";
        }
        LocalDateTime[] range = resolveTimeRange(time);
        LocalDateTime timeStart = range[0];
        LocalDateTime timeEnd = range[1];

        BigDecimal[] band = parsePriceBand(price);
        BigDecimal priceMin = band[0];
        BigDecimal priceMax = band[1];

        long showTotal = appSearchMapper.countSearchShows(keyword, city, artistId, timeStart, timeEnd,
                priceMin, priceMax, hasTicket);
        long offset = (long) (current - 1) * size;
        List<ShowCardVo> showRecords = appSearchMapper.selectSearchShows(
                keyword, city, artistId, timeStart, timeEnd, priceMin, priceMax, hasTicket, sort, offset, size);
        ShowVoConverter.enrichShowCards(showRecords, false);

        long artistTotal = appSearchMapper.countSearchArtists(keyword);
        List<SearchArtistRowVo> artistRows = appSearchMapper.selectSearchArtists(keyword, 0, 10);
        List<SearchArtistRowVo> pickerRows = appSearchMapper.selectSearchArtists(null, 0, 80);
        Set<Long> followedArtists = userId == null ? Collections.emptySet() : favoriteIds(userId, "artist");

        List<SearchArtistItemVo> artistRecords = artistRows.stream().map(row -> {
            long id = parseLongId(row.getId());
            return SearchVoConverter.toArtistItem(row, followedArtists.contains(id));
        }).collect(Collectors.toList());

        List<SearchArtistPickerVo> artistPicker = pickerRows.stream()
                .map(SearchVoConverter::toArtistPicker)
                .collect(Collectors.toList());

        long venueTotal = appSearchMapper.countSearchVenues(keyword, city);
        List<SearchVenueItemVo> venueRows = appSearchMapper.selectSearchVenues(keyword, city, 0, 10);
        Set<Long> followedVenues = userId == null ? Collections.emptySet() : favoriteIds(userId, "venue");
        venueRows.forEach(vo -> SearchVoConverter.applyVenueFollowed(vo,
                followedVenues.contains(parseLongId(vo.getId()))));
        List<SearchVenueItemVo> venueRecords = venueRows;

        List<WantRankItemVo> wantTop = appSearchMapper.selectWantRankTop3();

        WantRankVo wantRank = new WantRankVo();
        wantRank.setTitle("Livehouse 想看榜");
        wantRank.setTop3(wantTop);

        SearchResultVo data = new SearchResultVo();
        data.setShows(AppPageVo.of(showRecords, showTotal, current, size));
        data.setArtists(AppPageVo.of(artistRecords, artistTotal, 1, artistRecords.size()));
        data.setArtistPicker(artistPicker);
        data.setVenues(AppPageVo.of(venueRecords, venueTotal, 1, venueRecords.size()));
        data.setWantRank(wantRank);
        data.setKeyword(keyword == null ? "" : keyword);
        return data;
    }

    @Override
    public List<HotArtistVo> hotArtists(int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        return appSearchMapper.selectHotArtists(size);
    }

    private static LocalDateTime[] resolveTimeRange(String time) {
        LocalDateTime timeStart = null;
        LocalDateTime timeEnd = null;
        if (time != null && !time.isEmpty()) {
            LocalDate today = LocalDate.now();
            switch (time) {
                case "today":
                case "今天":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusDays(1).atStartOfDay();
                    break;
                case "weekend":
                case "本周末":
                    LocalDate sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                    timeStart = sat.atStartOfDay();
                    timeEnd = sat.plusDays(2).atStartOfDay();
                    break;
                case "month":
                case "本月":
                    timeStart = today.withDayOfMonth(1).atStartOfDay();
                    timeEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                    break;
                case "m1":
                case "一个月内":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(1).atStartOfDay();
                    break;
                case "m3":
                case "三个月内":
                    timeStart = today.atStartOfDay();
                    timeEnd = today.plusMonths(3).atStartOfDay();
                    break;
                default:
                    break;
            }
        }
        return new LocalDateTime[]{timeStart, timeEnd};
    }

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
                .stream().map(Favorite::getTargetId).collect(Collectors.toSet());
    }

    private static long parseLongId(String id) {
        if (id == null || id.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}