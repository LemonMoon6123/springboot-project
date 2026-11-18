package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Notice;
import com.livehouse.entity.Performance;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketType;
import com.livehouse.entity.Venue;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.Favorite;
import com.livehouse.mapper.AppShowMapper;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.mapper.NoticeMapper;
import com.livehouse.mapper.PerformanceMapper;
import com.livehouse.mapper.TicketTypeMapper;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.service.IShowService;
import com.livehouse.service.app.AppShowService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppShowServiceImpl implements AppShowService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHOW_TEXT = DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA);

    @Resource
    private AppShowMapper appShowMapper;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private IShowService showService;
    @Resource
    private VenueMapper venueMapper;
    @Resource
    private TicketTypeMapper ticketTypeMapper;
    @Resource
    private NoticeMapper noticeMapper;
    @Resource
    private FavoriteMapper favoriteMapper;

    @Override
    public Map<String, Object> home(String city, String type, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        Map<String, Object> data = list(city, type, null, "time", null, null, current, size, null);

        List<String> cityList = appShowMapper.selectDistinctCities();
        if (cityList.isEmpty()) {
            cityList = Arrays.asList("上海", "北京", "深圳", "成都");
        }
        List<String> types = appShowMapper.selectDistinctTypes();
        List<String> categories = new ArrayList<>();
        categories.add("全部");
        categories.addAll(types);

        long onSale = appShowMapper.countShowList(city, type, null, null, null);

        List<Map<String, Object>> banners = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        for (int i = 0; i < Math.min(3, records.size()); i++) {
            Map<String, Object> r = records.get(i);
            Map<String, Object> b = new HashMap<>();
            b.put("performanceId", r.get("performanceId"));
            b.put("poster", r.get("cover"));
            b.put("title", r.get("title"));
            banners.add(b);
        }

        Map<String, Object> home = new LinkedHashMap<>(data);
        home.put("city", city);
        home.put("cityList", cityList);
        home.put("categories", categories);
        home.put("onSaleCount", onSale);
        home.put("banners", banners);
        return home;
    }

    @Override
    public Map<String, Object> list(String city, String type, String date, String sort,
                                    Long venueId, Long artistId, int current, int size, Long userId) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        if (sort == null || sort.isEmpty()) {
            sort = "time";
        }
        long total = appShowMapper.countShowList(city, type, date, venueId, artistId);
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        long offset = (long) (current - 1) * size;

        List<Map<String, Object>> rows = appShowMapper.selectShowList(
                city, type, date, venueId, artistId, sort, offset, size);
        List<Map<String, Object>> records = rows.stream().map(this::toRecord).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("pages", pages);

        if (venueId != null) {
            Venue venue = venueMapper.selectById(venueId);
            if (venue != null) {
                data.put("venue", buildVenueBrief(venue, (int) total, userId));
            }
        }
        if (artistId != null) {
            Performance perf = performanceMapper.selectOne(new LambdaQueryWrapper<Performance>()
                    .eq(Performance::getArtistId, artistId).last("LIMIT 1"));
            if (perf != null) {
                Map<String, Object> artist = new LinkedHashMap<>();
                artist.put("id", String.valueOf(artistId));
                artist.put("name", perf.getArtistName());
                artist.put("avatar", null);
                artist.put("desc", perf.getType() + " · " + perf.getCity());
                artist.put("fansCount", 0);
                artist.put("showCount", total);
                artist.put("followed", false);
                data.put("artist", artist);
            }
        }
        return data;
    }

    @Override
    public Map<String, Object> detail(Long performanceId, Long showId, boolean noticesAll, Long userId) {
        Performance performance = performanceMapper.selectById(performanceId);
        if (performance == null || performance.getStatus() == null || performance.getStatus() != 2) {
            throw new StatusCodeException(StatusCode.SHOW_OFF_SHELF);
        }

        List<Show> sessions = showService.lambdaQuery()
                .eq(Show::getPerformanceId, performanceId)
                .in(Show::getStatus, 2, 3)
                .orderByAsc(Show::getStartTime)
                .list();
        if (sessions.isEmpty()) {
            throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
        }

        Show current = showId != null
                ? sessions.stream().filter(s -> s.getId().equals(showId)).findFirst().orElse(sessions.get(0))
                : sessions.get(0);

        Venue venue = venueMapper.selectById(current.getVenueId());

        List<TicketType> types = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getShowId, current.getId())
                .eq(TicketType::getStatus, 1)
                .orderByAsc(TicketType::getSortNo));
        int stockTotal = types.stream().mapToInt(t -> t.getLeftStock() == null ? 0 : t.getLeftStock()).sum();

        LambdaQueryWrapper<Notice> noticeQ = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .and(w -> w.isNull(Notice::getPerformanceId).or().eq(Notice::getPerformanceId, performanceId))
                .orderByAsc(Notice::getSortNo);
        long noticesTotal = noticeMapper.selectCount(noticeQ);
        if (!noticesAll) {
            noticeQ.last("LIMIT 8");
        }
        List<Notice> noticeList = noticeMapper.selectList(noticeQ);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("performanceId", String.valueOf(performanceId));
        data.put("showId", String.valueOf(current.getId()));
        data.put("title", performance.getTitle());
        data.put("artist", performance.getArtistName());
        data.put("artistId", performance.getArtistId() == null ? null : String.valueOf(performance.getArtistId()));
        data.put("artistsText", performance.getArtistName());
        data.put("eyebrow", (performance.getType() == null ? "LIVE" : performance.getType().toUpperCase())
                + " · " + (performance.getCity() == null ? "" : performance.getCity()));
        data.put("cover", performance.getPoster());
        data.put("posterTag", performance.getPosterTag());
        data.put("description", performance.getDescription());
        data.put("isRealName", performance.getIsRealName() == null ? 1 : performance.getIsRealName());
        data.put("limitTip", "实名制 · 每人限购 " + (types.isEmpty() ? 2 : types.get(0).getLimitPerUser()) + " 张");
        if (venue != null) {
            long venueUpcoming = appShowMapper.countShowList(venue.getCity(), null, null, venue.getId(), null);
            data.put("venue", buildVenueBrief(venue, (int) venueUpcoming, userId));
        }
        data.put("showTime", formatTime(current.getStartTime()));
        data.put("showTimeText", formatShowText(current.getStartTime()));
        data.put("stockTotal", stockTotal);
        data.put("stockText", "余票 " + stockTotal + " · 实名制");
        data.put("status", current.getStatus());
        data.put("sessions", sessions.stream().map(this::toSession).collect(Collectors.toList()));
        data.put("ticketTypes", types.stream().map(this::toTicketType).collect(Collectors.toList()));
        data.put("notices", noticeList.stream().map(this::toNotice).collect(Collectors.toList()));
        data.put("noticesTotal", noticesTotal);
        data.put("wantCount", performance.getWantCount() == null ? 0 : performance.getWantCount());
        boolean followed = false;
        if (userId != null) {
            long favCount = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getTargetType, "show")
                    .eq(Favorite::getTargetId, performanceId));
            followed = favCount > 0;
        }
        data.put("followed", followed);
        return data;
    }

    private Map<String, Object> toRecord(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("performanceId", String.valueOf(row.get("performanceId")));
        r.put("showId", String.valueOf(row.get("showId")));
        r.put("title", row.get("title"));
        r.put("artist", row.get("artist"));
        r.put("cover", row.get("cover"));
        r.put("posterTag", row.get("posterTag"));
        Object st = row.get("startTime");
        LocalDateTime start = st instanceof LocalDateTime ? (LocalDateTime) st : null;
        r.put("showTime", formatTime(start));
        r.put("showTimeText", formatShowText(start));
        r.put("venueName", row.get("venueName"));
        r.put("city", row.get("city"));
        Object mp = row.get("minPrice");
        BigDecimal minPrice = mp instanceof BigDecimal ? (BigDecimal) mp : null;
        r.put("minPrice", minPrice);
        r.put("priceText", minPrice == null ? "" : "¥" + minPrice.stripTrailingZeros().toPlainString() + " 起");
        r.put("status", row.get("status"));
        r.put("statusText", "售票中");
        r.put("isRealName", row.get("isRealName"));
        r.put("type", row.get("type"));
        String tag = row.get("posterTag") == null ? null : row.get("posterTag").toString();
        r.put("cardType", tag != null && !tag.isEmpty() ? "big" : "small");
        return r;
    }

    private Map<String, Object> toSession(Show s) {
        int left = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                        .eq(TicketType::getShowId, s.getId()).eq(TicketType::getStatus, 1))
                .stream().mapToInt(t -> t.getLeftStock() == null ? 0 : t.getLeftStock()).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("showId", String.valueOf(s.getId()));
        m.put("label", s.getSessionLabel() != null ? s.getSessionLabel() : formatShowText(s.getStartTime()));
        m.put("startTime", formatTime(s.getStartTime()));
        m.put("leftStock", left);
        m.put("status", s.getStatus());
        return m;
    }

    private Map<String, Object> toTicketType(TicketType t) {
        LocalDateTime now = LocalDateTime.now();
        int saleStatus = 1;
        String saleStatusText = "在售";
        if (t.getSaleStartTime() != null && t.getSaleStartTime().isAfter(now)) {
            saleStatus = 0;
            saleStatusText = "未开售";
        } else if (t.getSaleEndTime() != null && t.getSaleEndTime().isBefore(now)) {
            saleStatus = 2;
            saleStatusText = "已停售";
        }
        boolean soldOut = t.getLeftStock() == null || t.getLeftStock() <= 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(t.getId()));
        m.put("name", t.getName());
        m.put("description", t.getDescription());
        m.put("price", t.getPrice());
        m.put("leftStock", t.getLeftStock());
        m.put("totalStock", t.getTotalStock());
        m.put("limitPerUser", t.getLimitPerUser());
        m.put("perOrderLimit", t.getPerOrderLimit());
        m.put("saleStartTime", formatTime(t.getSaleStartTime()));
        m.put("saleEndTime", formatTime(t.getSaleEndTime()));
        m.put("saleStatus", saleStatus);
        m.put("saleStatusText", saleStatusText);
        m.put("soldOut", soldOut);
        m.put("hot", false);
        m.put("leftText", "余 " + (t.getLeftStock() == null ? 0 : t.getLeftStock()));
        return m;
    }

    private Map<String, Object> toNotice(Notice n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(n.getId()));
        m.put("category", n.getCategory());
        m.put("content", n.getContent());
        return m;
    }

    private Map<String, Object> buildVenueBrief(Venue venue, int upcomingCount, Long userId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", String.valueOf(venue.getId()));
        v.put("name", venue.getName());
        v.put("address", venue.getAddress());
        v.put("city", venue.getCity());
        v.put("longitude", venue.getLongitude());
        v.put("latitude", venue.getLatitude());
        v.put("traffic", "");
        v.put("tags", Arrays.asList("官方入驻", "站席观演"));
        v.put("followCount", venue.getFollowCount() == null ? 0 : venue.getFollowCount());
        boolean followed = false;
        if (userId != null) {
            long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getTargetType, "venue")
                    .eq(Favorite::getTargetId, venue.getId()));
            followed = cnt > 0;
        }
        v.put("followed", followed);
        v.put("upcomingCount", upcomingCount);
        return v;
    }

    private static String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    private static String formatShowText(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        try {
            return t.format(SHOW_TEXT);
        } catch (Exception e) {
            return t.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        }
    }
}