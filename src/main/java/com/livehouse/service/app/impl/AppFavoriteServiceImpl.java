package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.*;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.service.app.AppFavoriteService;
import com.livehouse.vo.app.common.AppPageVo;
import com.livehouse.vo.app.favorite.FavoriteActionVo;
import com.livehouse.vo.app.favorite.FavoriteRecordVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppFavoriteServiceImpl implements AppFavoriteService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private FavoriteMapper favoriteMapper;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private ArtistMapper artistMapper;
    @Resource
    private VenueMapper venueMapper;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private ShowMapper showMapper;
    @Resource
    private TicketTypeMapper ticketTypeMapper;
    @Resource
    private AppCommentService appCommentService;

    @Override
    @Transactional
    // 返回“followed: true”的Map
    public FavoriteActionVo add(Long userId, String targetType, Long targetId) {
        if (userId == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED, "请先登录");
        }
        if (targetType == null || targetId == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "targetType 或 targetId 不能为空");
        }
        String type = targetType.trim().toLowerCase();
        validateTarget(type, targetId);

        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId));
        if (existing != null) {
            return favoriteResult(type, targetId, true);
        }

        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setTargetType(type);
        fav.setTargetId(targetId);
        fav.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(fav);

        // 同时增加热度值
        if ("show".equals(type)) {
            performanceMapper.update(null, new LambdaUpdateWrapper<Performance>()
                    .eq(Performance::getId, targetId)
                    // 防止 want_count 是 NULL, NULL + 1 结果还是 NULL的情况。
                    .setSql("want_count = IFNULL(want_count,0) + 1"));
        } else if ("artist".equals(type)) {
            artistMapper.update(null, new LambdaUpdateWrapper<Artist>()
                    .eq(Artist::getId, targetId)
                    .setSql("follow_count = IFNULL(follow_count,0) + 1"));
        } else if ("venue".equals(type)) {
            venueMapper.update(null, new LambdaUpdateWrapper<Venue>()
                    .eq(Venue::getId, targetId)
                    .setSql("follow_count = IFNULL(follow_count,0) + 1"));
        }

        return favoriteResult(type, targetId, true);
    }

    @Override
    @Transactional
    // 返回followed: false的Map
    public FavoriteActionVo remove(Long userId, String targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        String type = targetType.trim().toLowerCase();
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId));
        if (existing == null) {
            return favoriteResult(type, targetId, false);
        }
        favoriteMapper.deleteById(existing.getId());

        if ("show".equals(type)) {
            performanceMapper.update(null, new LambdaUpdateWrapper<Performance>()
                    .eq(Performance::getId, targetId)
                    .ge(Performance::getWantCount, 1)
                    .setSql("want_count = want_count - 1"));
        } else if ("artist".equals(type)) {
            artistMapper.update(null, new LambdaUpdateWrapper<Artist>()
                    .eq(Artist::getId, targetId)
                    .ge(Artist::getFollowCount, 1)
                    .setSql("follow_count = follow_count - 1"));
        } else if ("venue".equals(type)) {
            venueMapper.update(null, new LambdaUpdateWrapper<Venue>()
                    .eq(Venue::getId, targetId)
                    .ge(Venue::getFollowCount, 1)
                    .setSql("follow_count = follow_count - 1"));
        }

        return favoriteResult(type, targetId, false);
    }

    private FavoriteActionVo favoriteResult(String type, Long targetId, boolean followed) {
        FavoriteActionVo data = new FavoriteActionVo();
        data.setFollowed(followed);
        if ("show".equals(type)) {
            Performance p = performanceMapper.selectById(targetId);
            if (p != null) {
                data.setWantCount(p.getWantCount() == null ? 0 : p.getWantCount());
            }
        } else if ("artist".equals(type)) {
            Artist a = artistMapper.selectById(targetId);
            if (a != null) {
                data.setFansCount(a.getFollowCount() == null ? 0 : a.getFollowCount());
            }
        } else if ("venue".equals(type)) {
            Venue v = venueMapper.selectById(targetId);
            if (v != null) {
                data.setFollowCount(v.getFollowCount() == null ? 0 : v.getFollowCount());
            }
        }
        return data;
    }

    @Override
    public AppPageVo<FavoriteRecordVo> list(Long userId, String type, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (type == null || type.isEmpty()) {
            type = "want";
        }
        List<FavoriteRecordVo> records;
        long total;
        switch (type) {
            case "seen":
                records = buildSeenList(userId);
                total = records.size();
                break;
            case "artist":
                records = listByType(userId, "artist");
                total = records.size();
                break;
            case "venue":
                records = listByType(userId, "venue");
                total = records.size();
                break;
            case "want":
            default:
                records = listWantShows(userId);
                total = records.size();
                break;
        }
        int from = (current - 1) * size;
        int to = Math.min(from + size, records.size());
        List<FavoriteRecordVo> page = from >= records.size() ? Collections.emptyList() : records.subList(from, to);
        return AppPageVo.of(page, total, current, size);
    }

    private void validateTarget(String type, Long targetId) {
        if ("show".equals(type)) {
            if (performanceMapper.selectById(targetId) == null) {
                throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
            }
        } else if ("artist".equals(type)) {
            if (artistMapper.selectById(targetId) == null) {
                throw new StatusCodeException(StatusCode.PARAM_ERROR, "艺人不存在");
            }
        } else if ("venue".equals(type)) {
            if (venueMapper.selectById(targetId) == null) {
                throw new StatusCodeException(StatusCode.PARAM_ERROR,
                        "场馆不存在（id=" + targetId + "），请刷新演出详情后重试");
            }
        } else {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "targetType 无效");
        }
    }

    private List<FavoriteRecordVo> listWantShows(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, "show")
                .orderByDesc(Favorite::getCreateTime));
        List<FavoriteRecordVo> out = new ArrayList<>();
        for (Favorite f : favs) {
            Performance p = performanceMapper.selectById(f.getTargetId());
            if (p == null) {
                continue;
            }
            Show show = resolveWantShow(f.getTargetId(), now);
            FavoriteRecordVo m = new FavoriteRecordVo();
            m.setTargetType("show");
            m.setTargetId(String.valueOf(f.getTargetId()));
            m.setPerformanceId(String.valueOf(f.getTargetId()));
            m.setTitle(p.getTitle());
            m.setCover(p.getPoster());
            m.setWantCount(p.getWantCount());
            m.setCreateTime(f.getCreateTime() == null ? null : f.getCreateTime().format(DT));
            m.setCanComment(false);
            m.setNote("想看");
            if (show != null) {
                m.setShowId(String.valueOf(show.getId()));
                m.setShowTimeText(show.getStartTime() == null ? ""
                        : show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
                Venue venue = show.getVenueId() == null ? null : venueMapper.selectById(show.getVenueId());
                m.setVenueName(venue == null ? "" : venue.getName());
                m.setPriceText(com.livehouse.util.PerformanceTicketPrice.priceText(ticketTypeMapper, f.getTargetId()));
            }
            out.add(m);
        }
        return out;
    }

    /** 想看列表进详情：优先进行中场次，其次最近未开演，否则最近一场（含已结束） */
    private Show resolveWantShow(Long performanceId, LocalDateTime now) {
        List<Show> shows = showMapper.selectList(new LambdaQueryWrapper<Show>()
                .eq(Show::getPerformanceId, performanceId)
                .in(Show::getStatus, 1, 2, 3, 4)
                .orderByAsc(Show::getStartTime));
        if (shows.isEmpty()) {
            return null;
        }
        for (Show s : shows) {
            if (isShowOngoing(s, now)) {
                return s;
            }
        }
        for (Show s : shows) {
            if (s.getStartTime() != null && s.getStartTime().isAfter(now)) {
                return s;
            }
        }
        Show latestPast = null;
        for (Show s : shows) {
            if (s.getStartTime() != null && !s.getStartTime().isAfter(now)) {
                latestPast = s;
            }
        }
        return latestPast != null ? latestPast : shows.get(shows.size() - 1);
    }

    private static boolean isShowOngoing(Show s, LocalDateTime now) {
        if (s.getStartTime() == null || s.getStartTime().isAfter(now)) {
            return false;
        }
        return s.getEndTime() == null || !s.getEndTime().isBefore(now);
    }

    private static String artistListDesc(Artist a) {
        if (a == null) {
            return "";
        }
        if (a.getIntro() != null && !a.getIntro().trim().isEmpty()) {
            String intro = a.getIntro().trim();
            return intro.length() > 40 ? intro.substring(0, 40) + "…" : intro;
        }
        String type = a.getType() == null ? "" : a.getType().trim();
        String city = a.getCity() == null ? "" : a.getCity().trim();
        if (!type.isEmpty() && !city.isEmpty()) {
            return type + " · " + city;
        }
        return !type.isEmpty() ? type : city;
    }

    //  根据Favorite表中的TargetType定位具体数据并读取
    private List<FavoriteRecordVo> listByType(Long userId, String targetType) {
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .orderByDesc(Favorite::getCreateTime));
        return favs.stream().map(f -> {
            FavoriteRecordVo m = new FavoriteRecordVo();
            m.setTargetType(targetType);
            m.setTargetId(String.valueOf(f.getTargetId()));
            m.setCreateTime(f.getCreateTime() == null ? null : f.getCreateTime().format(DT));
            m.setId(String.valueOf(f.getTargetId()));
            m.setFollowed(true);
            if ("artist".equals(targetType)) {
                Artist a = artistMapper.selectById(f.getTargetId());
                if (a != null) {
                    m.setName(a.getName());
                    m.setAvatar(a.getAvatar());
                    m.setIntro(a.getIntro());
                    m.setType(a.getType());
                    m.setCity(a.getCity());
                    m.setDesc(artistListDesc(a));
                }
            } else {
                Venue v = venueMapper.selectById(f.getTargetId());
                if (v != null) {
                    m.setName(v.getName());
                    m.setCity(v.getCity());
                    m.setAddress(v.getAddress());
                    String city = v.getCity() == null ? "" : v.getCity().trim();
                    String addr = v.getAddress() == null ? "" : v.getAddress().trim();
                    if (!city.isEmpty() && !addr.isEmpty()) {
                        m.setDesc(city + " · " + addr);
                    } else {
                        m.setDesc(!city.isEmpty() ? city : addr);
                    }
                }
            }
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 看过：电子票已核销，且场次已到开演时间（now >= start_time）。
     * 含「进行中」与「已结束」；未开演（当前时间早于 start_time）即使已核销也不进列表。
     */
    private List<FavoriteRecordVo> buildSeenList(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getUserId, userId)
                .eq(ElectronicTicket::getVerifyStatus, 1)
                .orderByDesc(ElectronicTicket::getVerifyTime));
        Map<Long, FavoriteRecordVo> byShow = new LinkedHashMap<>();
        for (ElectronicTicket t : tickets) {
            Long showId = t.getShowId();
            if (showId == null || byShow.containsKey(showId)) {
                continue;
            }
            Show show = showMapper.selectById(showId);
            if (show == null || show.getPerformanceId() == null) {
                continue;
            }
            if (show.getStartTime() == null || show.getStartTime().isAfter(now)) {
                continue;
            }
            Long perfId = show.getPerformanceId();
            Performance p = performanceMapper.selectById(perfId);
            if (p == null) {
                continue;
            }
            Venue venue = show.getVenueId() == null ? null : venueMapper.selectById(show.getVenueId());
            FavoriteRecordVo m = new FavoriteRecordVo();
            m.setTargetType("show");
            m.setTargetId(String.valueOf(perfId));
            m.setPerformanceId(String.valueOf(perfId));
            m.setShowId(String.valueOf(showId));
            m.setOrderId(t.getOrderId() == null ? null : String.valueOf(t.getOrderId()));
            m.setTitle(p.getTitle());
            m.setCover(p.getPoster());
            m.setVenueName(venue == null ? "" : venue.getName());
            m.setShowTimeText(show.getStartTime() == null ? ""
                    : show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
            m.setVerifyTime(t.getVerifyTime() == null ? null : t.getVerifyTime().format(DT));
            boolean canComment = t.getOrderId() != null && !appCommentService.hasCommentForOrder(t.getOrderId());
            m.setCanComment(canComment);
            m.setNote(canComment ? "可写观演笔记" : "已观演");
            byShow.put(showId, m);
        }
        return new ArrayList<>(byShow.values());
    }
}