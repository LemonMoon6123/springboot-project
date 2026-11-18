package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.*;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.service.app.AppFavoriteService;
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
    private AppCommentService appCommentService;

    @Override
    @Transactional
    // 返回“followed: true”的Map
    public Map<String, Object> add(Long userId, String targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
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
    public Map<String, Object> remove(Long userId, String targetType, Long targetId) {
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

    private Map<String, Object> favoriteResult(String type, Long targetId, boolean followed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("followed", followed);
        if ("show".equals(type)) {
            Performance p = performanceMapper.selectById(targetId);
            if (p != null) {
                data.put("wantCount", p.getWantCount() == null ? 0 : p.getWantCount());
            }
        }
        return data;
    }

    @Override
    public Map<String, Object> list(Long userId, String type, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (type == null || type.isEmpty()) {
            type = "want";
        }
        List<Map<String, Object>> records;
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
        List<Map<String, Object>> page = from >= records.size() ? Collections.emptyList() : records.subList(from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("pages", total == 0 ? 0 : (total + size - 1) / size);
        return data;
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
                throw new StatusCodeException(StatusCode.PARAM_ERROR, "场馆不存在");
            }
        } else {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "targetType 无效");
        }
    }

    private List<Map<String, Object>> listWantShows(Long userId) {
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, "show")
                .orderByDesc(Favorite::getCreateTime));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Favorite f : favs) {
            Performance p = performanceMapper.selectById(f.getTargetId());
            if (p == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetType", "show");
            m.put("targetId", String.valueOf(f.getTargetId()));
            m.put("performanceId", String.valueOf(f.getTargetId()));
            m.put("title", p.getTitle());
            m.put("cover", p.getPoster());
            m.put("wantCount", p.getWantCount());
            m.put("createTime", f.getCreateTime() == null ? null : f.getCreateTime().format(DT));
            m.put("canComment", false);
            m.put("note", "想看");
            out.add(m);
        }
        return out;
    }

    //  根据Favorite表中的TargetType定位具体数据并读取
    private List<Map<String, Object>> listByType(Long userId, String targetType) {
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .orderByDesc(Favorite::getCreateTime));
        return favs.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetType", targetType);
            m.put("targetId", String.valueOf(f.getTargetId()));
            m.put("createTime", f.getCreateTime() == null ? null : f.getCreateTime().format(DT));
            if ("artist".equals(targetType)) {
                Artist a = artistMapper.selectById(f.getTargetId());
                if (a != null) {
                    m.put("name", a.getName());
                    m.put("avatar", a.getAvatar());
                }
            } else {
                Venue v = venueMapper.selectById(f.getTargetId());
                if (v != null) {
                    m.put("name", v.getName());
                    m.put("city", v.getCity());
                }
            }
            return m;
        }).collect(Collectors.toList());
    }

    // 看过的演出 -> 电子票已经核销过。
    private List<Map<String, Object>> buildSeenList(Long userId) {
        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getUserId, userId)
                .eq(ElectronicTicket::getVerifyStatus, 1));
        Map<Long, Map<String, Object>> byPerf = new LinkedHashMap<>(); // 存放演出对应信息
        for (ElectronicTicket t : tickets) {
            Show show = showMapper.selectById(t.getShowId());
            if (show == null || show.getPerformanceId() == null) {
                continue;
            }
            Long perfId = show.getPerformanceId();
            if (byPerf.containsKey(perfId)) {
                continue;
            }
            Performance p = performanceMapper.selectById(perfId);
            if (p == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetType", "show");
            m.put("targetId", String.valueOf(perfId));
            m.put("title", p.getTitle());
            m.put("cover", p.getPoster());
            m.put("verifyTime", t.getVerifyTime() == null ? null : t.getVerifyTime().format(DT));
            boolean canComment = t.getOrderId() != null && !appCommentService.hasCommentForOrder(t.getOrderId());
            m.put("canComment", canComment);
            m.put("note", canComment ? "可写观演笔记" : "已观演");
            byPerf.put(perfId, m);
        }
        return new ArrayList<>(byPerf.values());
    }
}