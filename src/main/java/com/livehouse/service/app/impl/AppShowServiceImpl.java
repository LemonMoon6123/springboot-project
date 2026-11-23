package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Artist;
import com.livehouse.entity.Favorite;
import com.livehouse.entity.Notice;
import com.livehouse.entity.Performance;
import com.livehouse.entity.Show;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.TicketType;
import com.livehouse.entity.Venue;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.AppShowMapper;
import com.livehouse.mapper.ArtistMapper;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.mapper.NoticeMapper;
import com.livehouse.mapper.PerformanceMapper;
import com.livehouse.mapper.TicketTypeMapper;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.service.IShowService;
import com.livehouse.service.app.AppShowService;
import com.livehouse.service.app.FlashSaleBannerService;
import com.livehouse.util.PerformanceDetailImages;
import com.livehouse.vo.app.convert.ShowVoConverter;
import com.livehouse.vo.app.show.ArtistBriefVo;
import com.livehouse.vo.app.show.HomeBannerVo;
import com.livehouse.vo.app.show.HomeVo;
import com.livehouse.vo.app.show.ShowCardVo;
import com.livehouse.vo.app.show.ShowDetailVo;
import com.livehouse.vo.app.show.ShowListVo;
import com.livehouse.vo.app.show.ShowNoticeVo;
import com.livehouse.vo.app.show.ShowSessionVo;
import com.livehouse.vo.app.show.ShowTicketTypeVo;
import com.livehouse.vo.app.show.VenueBriefVo;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppShowServiceImpl implements AppShowService {

    @Resource
    private AppShowMapper appShowMapper;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private IShowService showService;
    @Resource
    private VenueMapper venueMapper;
    @Resource
    private ArtistMapper artistMapper;
    @Resource
    private TicketTypeMapper ticketTypeMapper;
    @Resource
    private NoticeMapper noticeMapper;
    @Resource
    private FavoriteMapper favoriteMapper;
    @Resource
    private FlashSaleBannerService flashSaleBannerService;

    @Override
    public HomeVo home(String city, String type, int current, int size) {
        ShowListVo list = list(city, type, null, "time", null, null, current, size, null);
        HomeVo home = new HomeVo();
        home.setRecords(list.getRecords());
        home.setTotal(list.getTotal());
        home.setCurrent(list.getCurrent());
        home.setSize(list.getSize());
        home.setPages(list.getPages());

        List<String> cityList = appShowMapper.selectDistinctCities();
        if (cityList.isEmpty()) {
            cityList = Arrays.asList("上海", "北京", "深圳", "成都");
        }
        List<String> types = appShowMapper.selectDistinctTypes(city);
        List<String> categories = new ArrayList<>();
        categories.add("全部");
        categories.addAll(types);

        home.setCity(city);
        home.setCityList(cityList);
        home.setCategories(categories);
        home.setOnSaleCount(appShowMapper.countShowList(city, type, null, null, null));

        List<HomeBannerVo> banners = new ArrayList<>();
        List<ShowCardVo> records = list.getRecords() == null ? Collections.emptyList() : list.getRecords();
        for (int i = 0; i < Math.min(3, records.size()); i++) {
            banners.add(ShowVoConverter.toHomeBanner(records.get(i)));
        }
        home.setBanners(banners);
        home.setFlashSale(flashSaleBannerService.current());
        return home;
    }

    @Override
    public ShowListVo list(String city, String type, String date, String sort,
                           Long venueId, Long artistId, int current, int size, Long userId) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (sort == null || sort.isEmpty()) {
            sort = "time";
        }
        long total = appShowMapper.countShowList(city, type, date, venueId, artistId);
        long offset = (long) (current - 1) * size;

        List<ShowCardVo> records = appShowMapper.selectShowList(
                city, type, date, venueId, artistId, sort, offset, size);
        ShowVoConverter.enrichShowCards(records, true);

        ShowListVo data = ShowListVo.fromPage(records, total, current, size);

        if (venueId != null) {
            Venue venue = venueMapper.selectById(venueId);
            if (venue != null) {
                boolean followed = isVenueFollowed(userId, venue.getId());
                data.setVenue(ShowVoConverter.toVenueBrief(venue, (int) total, followed));
            }
        }
        if (artistId != null) {
            Artist artist = artistMapper.selectById(artistId);
            boolean followed = isArtistFollowed(userId, artistId);
            data.setArtist(ShowVoConverter.toArtistBrief(artist, artistId, (int) total, followed));
        }
        return data;
    }

    @Override
    public ShowDetailVo detail(Long performanceId, Long showId, boolean noticesAll, Long userId) {
        Show currentById = null;
        if (showId != null) {
            currentById = showService.getById(showId);
            if (currentById == null) {
                throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
            }
            performanceId = currentById.getPerformanceId();
        }

        Performance performance = performanceMapper.selectById(performanceId);
        if (performance == null) {
            throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
        }
        if (showId == null && (performance.getStatus() == null || performance.getStatus() != 2)) {
            throw new StatusCodeException(StatusCode.SHOW_OFF_SHELF);
        }
        final Long perfId = performanceId;

        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Show> showQ = new LambdaQueryWrapper<Show>()
                .eq(Show::getPerformanceId, perfId)
                .in(Show::getStatus, 1, 2, 3, 4)
                .and(w -> w.gt(Show::getStartTime, now)
                        .or(w2 -> w2.le(Show::getStartTime, now)
                                .and(w3 -> w3.isNull(Show::getEndTime).or().ge(Show::getEndTime, now))))
                .orderByAsc(true, Show::getStartTime);
        List<Show> switchable = showService.list(showQ);
        switchable = switchable.stream()
                .filter(s -> sessionListable(s.getId(), now) || isOngoing(s, now))
                .collect(Collectors.toList());

        Show current;
        if (currentById != null) {
            current = currentById;
        } else {
            List<Show> onSaleFuture = switchable.stream()
                    .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                    .filter(s -> sessionListable(s.getId(), now))
                    .collect(Collectors.toList());
            if (onSaleFuture.isEmpty()) {
                throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
            }
            current = onSaleFuture.get(0);
        }

        List<Show> sessions = new ArrayList<>(switchable);
        if (sessions.stream().noneMatch(s -> s.getId().equals(current.getId()))) {
            sessions.add(current);
        }
        sessions.sort(Comparator.comparing(Show::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        Venue venue = venueMapper.selectById(current.getVenueId());

        List<TicketType> types = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getShowId, current.getId())
                .eq(TicketType::getStatus, 1)
                .orderByAsc(TicketType::getSortNo));
        int stockTotal = types.stream().mapToInt(t -> t.getLeftStock() == null ? 0 : t.getLeftStock()).sum();

        LambdaQueryWrapper<Notice> noticeQ = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .and(w -> w.isNull(Notice::getPerformanceId).or().eq(Notice::getPerformanceId, perfId))
                .orderByAsc(Notice::getSortNo);
        long noticesTotal = noticeMapper.selectCount(noticeQ);
        if (!noticesAll) {
            noticeQ.last("LIMIT 8");
        }
        List<Notice> noticeList = noticeMapper.selectList(noticeQ);

        ShowDetailVo vo = new ShowDetailVo();
        vo.setPerformanceId(String.valueOf(performanceId));
        vo.setShowId(String.valueOf(current.getId()));
        vo.setTitle(performance.getTitle());

        Long perfArtistId = performance.getArtistId();
        String perfArtistName = StrUtil.isBlank(performance.getArtistName())
                ? null : performance.getArtistName().trim();
        if (perfArtistId == null && perfArtistName == null) {
            vo.setHasArtist(false);
        } else {
            vo.setArtist(perfArtistName);
            vo.setArtistId(perfArtistId == null ? null : String.valueOf(perfArtistId));
            vo.setArtistsText(perfArtistName);
            vo.setHasArtist(perfArtistId != null);
        }

        String typeLabel = performance.getType() == null ? "LIVE" : performance.getType().toUpperCase();
        String showCity = venue != null && venue.getCity() != null ? venue.getCity().trim() : "";
        vo.setEyebrow(showCity.isEmpty() ? typeLabel : typeLabel + " · " + showCity);
        vo.setCover(performance.getPoster());
        vo.setPosterTag(performance.getPosterTag());
        vo.setDescription(performance.getDescription());
        vo.setDetailImages(PerformanceDetailImages.parseList(performance.getDetailImages()));
        vo.setIsRealName(performance.getIsRealName() == null ? 1 : performance.getIsRealName());
        vo.setLimitTip("实名制 · 每人限购 " + (types.isEmpty() ? 2 : types.get(0).getLimitPerUser()) + " 张");

        if (venue != null) {
            long venueUpcoming = appShowMapper.countShowList(venue.getCity(), null, null, venue.getId(), null);
            vo.setVenueId(String.valueOf(venue.getId()));
            vo.setVenue(ShowVoConverter.toVenueBrief(venue, (int) venueUpcoming, isVenueFollowed(userId, venue.getId())));
        }

        vo.setShowTime(ShowVoConverter.formatTime(current.getStartTime()));
        vo.setShowEndTime(ShowVoConverter.formatTime(current.getEndTime()));
        vo.setDurationMinutes(durationMinutes(current.getStartTime(), current.getEndTime()));
        vo.setShowTimeText(ShowVoConverter.formatShowText(current.getStartTime()));
        vo.setStockTotal(stockTotal);
        vo.setStockText("余票 " + stockTotal + " · 实名制");
        vo.setStatus(current.getStatus());

        List<ShowSessionVo> sessionVos = sessions.stream()
                .map(s -> ShowVoConverter.toSession(s, sessionLeftStock(s.getId())))
                .collect(Collectors.toList());
        vo.setSessions(sessionVos);

        List<ShowTicketTypeVo> typeVos = types.stream()
                .map(t -> ShowVoConverter.toTicketType(t, now))
                .collect(Collectors.toList());
        vo.setTicketTypes(typeVos);

        List<ShowNoticeVo> noticeVos = noticeList.stream()
                .map(ShowVoConverter::toNotice)
                .collect(Collectors.toList());
        vo.setNotices(noticeVos);
        vo.setNoticesTotal(noticesTotal);
        vo.setWantCount(performance.getWantCount() == null ? 0 : performance.getWantCount());
        vo.setFollowed(isShowFollowed(userId, performanceId));
        vo.setArtistFollowed(perfArtistId != null && isArtistFollowed(userId, perfArtistId));

        if (perfArtistId != null) {
            Artist artist = artistMapper.selectById(perfArtistId);
            vo.setArtistFansCount(artist == null || artist.getFollowCount() == null ? 0 : artist.getFollowCount());
            vo.setArtistAvatar(artist == null ? null : artist.getAvatar());
        } else {
            vo.setArtistFansCount(0);
        }

        ShowVoConverter.applyPurchaseState(vo, current, types, now);
        return vo;
    }

    private int sessionLeftStock(Long showId) {
        return ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                        .eq(TicketType::getShowId, showId).eq(TicketType::getStatus, 1))
                .stream().mapToInt(t -> t.getLeftStock() == null ? 0 : t.getLeftStock()).sum();
    }

    private boolean isShowFollowed(Long userId, Long performanceId) {
        if (userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, "show")
                .eq(Favorite::getTargetId, performanceId)) > 0;
    }

    private boolean isArtistFollowed(Long userId, Long artistId) {
        if (userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, "artist")
                .eq(Favorite::getTargetId, artistId)) > 0;
    }

    private boolean isVenueFollowed(Long userId, Long venueId) {
        if (userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, "venue")
                .eq(Favorite::getTargetId, venueId)) > 0;
    }

    private static Integer durationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }
        long mins = java.time.Duration.between(start, end).toMinutes();
        if (mins <= 0 || mins > Integer.MAX_VALUE) {
            return null;
        }
        return (int) mins;
    }

    private static boolean isOngoing(Show s, LocalDateTime now) {
        if (s.getStartTime() == null || s.getStartTime().isAfter(now)) {
            return false;
        }
        return s.getEndTime() == null || !s.getEndTime().isBefore(now);
    }

    private boolean sessionListable(Long showId, LocalDateTime now) {
        List<TicketType> listed = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getShowId, showId)
                .eq(TicketType::getStatus, 1));
        if (listed.isEmpty()) {
            return false;
        }
        return listed.stream().anyMatch(t -> t.getSaleEndTime() != null && !t.getSaleEndTime().isBefore(now));
    }
}