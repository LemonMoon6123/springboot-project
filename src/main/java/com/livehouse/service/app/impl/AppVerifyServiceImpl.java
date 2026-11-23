package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.mapper.PerformanceMapper;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.service.ICheckInRecordService;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.service.IUserService;
import com.livehouse.service.app.AppVerifyService;
import com.livehouse.service.app.VenueStaffService;
import com.livehouse.vo.app.verify.VerifyDuplicateVo;
import com.livehouse.vo.app.verify.VerifyHourlyTrendVo;
import com.livehouse.vo.app.verify.VerifyRecentRecordVo;
import com.livehouse.vo.app.verify.VerifyScanResultVo;
import com.livehouse.vo.app.verify.VerifySessionVo;
import com.livehouse.vo.app.verify.VerifyStatsVo;
import com.livehouse.service.app.credit.CreditAccountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AppVerifyServiceImpl implements AppVerifyService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHOW_TEXT = DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA);

    @Resource
    private VenueStaffService venueStaffService;
    @Resource
    private IShowService showService;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private VenueMapper venueMapper;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    @Resource
    private ICheckInRecordService checkInRecordService;
    @Resource
    private ITicketTypeService ticketTypeService;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CreditAccountService creditAccountService;

    @Override
    public List<VerifySessionVo> sessions(Long userId) {
        List<Long> venueIds = venueStaffService.listVenueIdsForStaff(userId);
        if (venueIds.isEmpty()) {
            throw new StatusCodeException(StatusCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        // 可核销场次：所属场馆、演出进行中（start<=now 且未结束）
        List<Show> shows = showService.lambdaQuery()
                .in(Show::getVenueId, venueIds)
                .in(Show::getStatus, 2, 3, 4)
                .isNotNull(Show::getStartTime)
                .le(Show::getStartTime, now)
                .and(w -> w.isNull(Show::getEndTime).or().gt(Show::getEndTime, now))
                .orderByDesc(Show::getStartTime)
                .list();

        return shows.stream().map(s -> {
            Performance p = s.getPerformanceId() != null ? performanceMapper.selectById(s.getPerformanceId()) : null;
            Venue v = venueMapper.selectById(s.getVenueId());
            long total = ticketOrderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getShowId, s.getId())
                    .eq(TicketOrder::getPayStatus, 1));
            long verified = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                    .eq(ElectronicTicket::getShowId, s.getId())
                    .eq(ElectronicTicket::getVerifyStatus, 1));
            boolean started = s.getStartTime() != null && !now.isBefore(s.getStartTime());
            boolean notEnded = s.getEndTime() == null || !now.isAfter(s.getEndTime());
            VerifySessionVo m = new VerifySessionVo();
            m.setShowId(String.valueOf(s.getId()));
            m.setPerformanceTitle(p != null ? p.getTitle() : s.getTitle());
            m.setLabel(buildSessionLabel(s));
            m.setVenueId(String.valueOf(s.getVenueId()));
            m.setVenueName(v == null ? "" : v.getName());
            m.setStartTime(s.getStartTime() == null ? null : s.getStartTime().format(DT));
            m.setCurrent(started && notEnded);
            m.setTotalTickets(total);
            m.setVerifiedCount(verified);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VerifyScanResultVo scan(Long operatorUserId, Long showId, String ticketCode, String shortCode, String gate) {
        Show show = showService.getById(showId);
        if (show == null) {
            throw new StatusCodeException(StatusCode.SESSION_NOT_FOUND);
        }
        venueStaffService.requireStaffAtVenue(operatorUserId, show.getVenueId());

        LocalDateTime now = LocalDateTime.now();
        if (show.getStartTime() != null && now.isBefore(show.getStartTime())) {
            recordFail(null, showId, operatorUserId, gate, "演出尚未开始", show);
            throw new StatusCodeException(StatusCode.VERIFY_NOT_STARTED, "演出尚未开始，请开演后再核销");
        }
        if (show.getEndTime() != null && !now.isBefore(show.getEndTime())) {
            recordFail(null, showId, operatorUserId, gate, "演出已结束", show);
            throw new StatusCodeException(StatusCode.VERIFY_ENDED, "演出已结束，无法继续核销");
        }

        ElectronicTicket ticket = resolveTicket(showId, ticketCode, shortCode);
        if (ticket == null) {
            recordFail(null, showId, operatorUserId, gate, "无效码", show);
            throw new StatusCodeException(StatusCode.TICKET_CODE_INVALID);
        }
        if (!ticket.getShowId().equals(showId)) {
            recordFail(ticket, showId, operatorUserId, gate, "跨场次", show);
            throw new StatusCodeException(StatusCode.VERIFY_CROSS_SHOW);
        }
        if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 2) {
            recordFail(ticket, showId, operatorUserId, gate, "已退票", show);
            throw new StatusCodeException(StatusCode.TICKET_REFUNDED);
        }
        if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 1) {
            recordFail(ticket, showId, operatorUserId, gate, "重复入场", show);
            String dupGate = resolveGate(gate, ticket, show);
            CheckInRecord prev = checkInRecordService.lambdaQuery()
                    .eq(CheckInRecord::getTicketId, ticket.getId())
                    .eq(CheckInRecord::getResult, 1)
                    .orderByDesc(CheckInRecord::getCheckTime)
                    .last("LIMIT 1").one();
            VerifyDuplicateVo dup = new VerifyDuplicateVo();
            dup.setResult("DUPLICATE");
            dup.setCheckTime(prev != null && prev.getCheckTime() != null ? prev.getCheckTime().format(DT) : null);
            dup.setGate(prev != null && prev.getGate() != null ? prev.getGate() : dupGate);
            throw new StatusCodeException(StatusCode.TICKET_ALREADY_USED, "该票已核销入场", dup);
        }

        String effectiveGate = resolveGate(gate, ticket, show);

        ticket.setVerifyStatus(1);
        ticket.setVerifyTime(now);
        ticket.setGate(effectiveGate);
        electronicTicketMapper.updateById(ticket);

        com.livehouse.entity.User u = userService.getById(operatorUserId);
        String operatorName = u != null && u.getNickName() != null ? u.getNickName() : "工作人员";

        CheckInRecord record = new CheckInRecord();
        record.setTicketId(ticket.getId());
        record.setUserId(ticket.getUserId());
        record.setShowId(showId);
        record.setOperatorId(operatorUserId);
        record.setOperatorName(operatorName);
        record.setGate(effectiveGate);
        record.setResult(1);
        record.setCheckTime(now);
        checkInRecordService.save(record);
        syncOrderEntryAfterVerify(ticket.getOrderId());
        int creditGain = creditAccountService.grantAfterVerify(ticket.getId());

        if (ticketCode != null && !ticketCode.isEmpty()) {
            stringRedisTemplate.delete("ticket:present:" + ticketCode);
        }

        Performance perf = show.getPerformanceId() != null ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(ticket.getTicketTypeId());

        VerifyScanResultVo data = new VerifyScanResultVo();
        data.setResult("SUCCESS");
        data.setResultText("核销成功");
        data.setTicketId(String.valueOf(ticket.getId()));
        data.setDisplayCode(ticket.getDisplayCode());
        data.setTitle(perf != null ? perf.getTitle() : show.getTitle());
        data.setTicketTypeName(tt == null ? "" : tt.getName());
        data.setAudienceName(ticket.getAudienceName());
        data.setGate(effectiveGate);
        data.setCheckTime(now.format(DT));
        data.setOperatorName(operatorName);
        if (creditGain > 0) {
            data.setLevelCreditGain(creditGain);
            data.setAvailableCreditGain(creditGain);
        }
        return data;
    }

    @Override
    public VerifyStatsVo stats(Long userId, Long showId) {
        Show show = showService.getById(showId);
        if (show == null) {
            throw new StatusCodeException(StatusCode.SESSION_NOT_FOUND);
        }
        venueStaffService.requireStaffAtVenue(userId, show.getVenueId());

        long totalTickets = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getShowId, showId)
                .in(ElectronicTicket::getVerifyStatus, 0, 1));
        long verifiedCount = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getShowId, showId)
                .eq(ElectronicTicket::getVerifyStatus, 1));
        long pendingCount = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getShowId, showId)
                .eq(ElectronicTicket::getVerifyStatus, 0));

        List<CheckInRecord> recent = checkInRecordService.lambdaQuery()
                .eq(CheckInRecord::getShowId, showId)
                .orderByDesc(CheckInRecord::getCheckTime)
                .last("LIMIT 10")
                .list();

        List<VerifyRecentRecordVo> recentRecords = recent.stream().map(r -> {
            ElectronicTicket t = electronicTicketMapper.selectById(r.getTicketId());
            VerifyRecentRecordVo m = new VerifyRecentRecordVo();
            m.setTicketId(String.valueOf(r.getTicketId()));
            m.setShortCode(t == null ? null : t.getShortCode());
            m.setDisplayCode(t == null ? null : t.getDisplayCode());
            m.setAudienceName(t == null ? null : t.getAudienceName());
            m.setGate(displayGate(r.getGate(), t, show));
            m.setCheckTime(r.getCheckTime() == null ? null : r.getCheckTime().format(DT));
            m.setOperatorName(r.getOperatorName());
            boolean ok = r.getResult() != null && r.getResult() == 1;
            m.setResult(ok ? "OK" : "DUPLICATE");
            m.setResultFlag(ok ? 1 : 0);
            return m;
        }).collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<CheckInRecord> todaySuccess = checkInRecordService.lambdaQuery()
                .eq(CheckInRecord::getShowId, showId)
                .eq(CheckInRecord::getResult, 1)
                .ge(CheckInRecord::getCheckTime, dayStart)
                .list();
        Map<Integer, Long> hourCount = todaySuccess.stream()
                .filter(r -> r.getCheckTime() != null)
                .collect(Collectors.groupingBy(r -> r.getCheckTime().getHour(), Collectors.counting()));
        List<VerifyHourlyTrendVo> hourlyTrend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            int h = now.minusHours(i).getHour();
            VerifyHourlyTrendVo bucket = new VerifyHourlyTrendVo();
            bucket.setHour(String.format("%02d:00", h));
            bucket.setCount(hourCount.getOrDefault(h, 0L));
            hourlyTrend.add(bucket);
        }

        VerifyStatsVo data = new VerifyStatsVo();
        data.setTotalTickets(totalTickets);
        data.setVerifiedCount(verifiedCount);
        data.setPendingCount(pendingCount);
        data.setVerifyRate(totalTickets == 0 ? 0 : Math.round(verifiedCount * 1000.0 / totalTickets) / 10.0);
        data.setHourlyTrend(hourlyTrend);
        data.setRecentRecords(recentRecords);
        data.setRecent(recentRecords);
        return data;
    }

    private ElectronicTicket resolveTicket(Long showId, String ticketCode, String shortCode) {
        if (ticketCode != null && !ticketCode.isEmpty()) {
            String key = ticketCode.startsWith("ticket:present:") ? ticketCode : "ticket:present:" + ticketCode;
            String ticketIdStr = stringRedisTemplate.opsForValue().get(key);
            if (ticketIdStr != null) {
                return electronicTicketMapper.selectById(Long.parseLong(ticketIdStr));
            }
        }
        String normalized = normalizeShortCode(shortCode);
        if (normalized != null) {
            ElectronicTicket byShort = electronicTicketMapper.selectOne(new LambdaQueryWrapper<ElectronicTicket>()
                    .eq(ElectronicTicket::getShortCode, normalized)
                    .last("LIMIT 1"));
            if (byShort != null) {
                return byShort;
            }
            return electronicTicketMapper.selectOne(new LambdaQueryWrapper<ElectronicTicket>()
                    .apply("RIGHT(REPLACE(display_code, ' ', ''), 6) = {0}", normalized)
                    .last("LIMIT 1"));
        }
        return null;
    }

    private static String normalizeShortCode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() > 6) {
            digits = digits.substring(digits.length() - 6);
        }
        while (digits.length() < 6) {
            digits = "0" + digits;
        }
        return digits;
    }

    private String buildSessionLabel(Show s) {
        if (s.getStartTime() != null) {
            try {
                return s.getStartTime().format(SHOW_TEXT);
            } catch (Exception e) {
                return s.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
            }
        }
        if (s.getSessionLabel() != null && !s.getSessionLabel().trim().isEmpty()) {
            return s.getSessionLabel().trim();
        }
        return "";
    }

    private void recordFail(ElectronicTicket ticket, Long showId, Long operatorId, String requestGate, String reason, Show show) {
        if (ticket == null) {
            return;
        }
        CheckInRecord record = new CheckInRecord();
        record.setTicketId(ticket.getId());
        record.setUserId(ticket.getUserId());
        record.setShowId(showId);
        record.setOperatorId(operatorId);
        record.setGate(resolveGate(requestGate, ticket, show));
        record.setResult(0);
        record.setFailReason(reason);
        record.setCheckTime(LocalDateTime.now());
        checkInRecordService.save(record);
    }

    /** 请求未带 gate 时：电子票 gate → 场次 gate_config 首项 → 默认 A */
    private static String resolveGate(String requestGate, ElectronicTicket ticket, Show show) {
        if (requestGate != null && !requestGate.trim().isEmpty()) {
            return requestGate.trim();
        }
        if (ticket != null && ticket.getGate() != null && !ticket.getGate().trim().isEmpty()) {
            return ticket.getGate().trim();
        }
        if (show != null && show.getGateConfig() != null && !show.getGateConfig().trim().isEmpty()) {
            String first = show.getGateConfig().split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return "A";
    }

    private static String displayGate(String recordGate, ElectronicTicket ticket, Show show) {
        if (recordGate != null && !recordGate.trim().isEmpty()) {
            return recordGate.trim();
        }
        return resolveGate(null, ticket, show);
    }

    /** 订单下全部电子票已核销时，更新 entry_status=1（已观演） */
    private void syncOrderEntryAfterVerify(Long orderId) {
        if (orderId == null) {
            return;
        }
        long total = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId));
        if (total <= 0) {
            return;
        }
        long verified = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId)
                .eq(ElectronicTicket::getVerifyStatus, 1));
        if (verified >= total) {
            TicketOrder patch = new TicketOrder();
            patch.setId(orderId);
            patch.setEntryStatus(1);
            ticketOrderMapper.updateById(patch);
        }
    }
}