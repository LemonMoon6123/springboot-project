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
import com.livehouse.service.app.credit.CreditAccountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppVerifyServiceImpl implements AppVerifyService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    public List<Map<String, Object>> sessions(Long userId) {
        List<Long> venueIds = venueStaffService.listVenueIdsForStaff(userId);
        if (venueIds.isEmpty()) {
            throw new StatusCodeException(StatusCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Show> shows = showService.lambdaQuery()
                .in(Show::getVenueId, venueIds)
                .in(Show::getStatus, 2, 4)
                .ge(Show::getEndTime, now.minusHours(6))
                .orderByAsc(Show::getStartTime)
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("showId", String.valueOf(s.getId()));
            m.put("performanceTitle", p != null ? p.getTitle() : s.getTitle());
            m.put("label", s.getSessionLabel() != null ? s.getSessionLabel() : "");
            m.put("venueId", String.valueOf(s.getVenueId()));
            m.put("venueName", v == null ? "" : v.getName());
            m.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(DT));
            m.put("current", s.getStartTime() != null && s.getEndTime() != null
                    && !now.isBefore(s.getStartTime().minusHours(2)) && !now.isAfter(s.getEndTime()));
            m.put("totalTickets", total);
            m.put("verifiedCount", verified);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Map<String, Object> scan(Long operatorUserId, Long showId, String ticketCode, String shortCode, String gate) {
        Show show = showService.getById(showId);
        if (show == null) {
            throw new StatusCodeException(StatusCode.SESSION_NOT_FOUND);
        }
        venueStaffService.requireStaffAtVenue(operatorUserId, show.getVenueId());

        LocalDateTime now = LocalDateTime.now();
        if (show.getStartTime() != null && now.isBefore(show.getStartTime().minusHours(2))) {
            recordFail(null, showId, operatorUserId, gate, "未到入场时间");
            throw new StatusCodeException(StatusCode.VERIFY_NOT_STARTED);
        }
        if (show.getEndTime() != null && now.isAfter(show.getEndTime())) {
            recordFail(null, showId, operatorUserId, gate, "演出已结束");
            throw new StatusCodeException(StatusCode.VERIFY_ENDED);
        }

        ElectronicTicket ticket = resolveTicket(showId, ticketCode, shortCode);
        if (ticket == null) {
            recordFail(null, showId, operatorUserId, gate, "无效码");
            throw new StatusCodeException(StatusCode.TICKET_CODE_INVALID);
        }
        if (!ticket.getShowId().equals(showId)) {
            recordFail(ticket, showId, operatorUserId, gate, "跨场次");
            throw new StatusCodeException(StatusCode.VERIFY_CROSS_SHOW);
        }
        if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 2) {
            recordFail(ticket, showId, operatorUserId, gate, "已退票");
            throw new StatusCodeException(StatusCode.TICKET_REFUNDED);
        }
        if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() == 1) {
            recordFail(ticket, showId, operatorUserId, gate, "重复入场");
            CheckInRecord prev = checkInRecordService.lambdaQuery()
                    .eq(CheckInRecord::getTicketId, ticket.getId())
                    .eq(CheckInRecord::getResult, 1)
                    .orderByDesc(CheckInRecord::getCheckTime)
                    .last("LIMIT 1").one();
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("result", "DUPLICATE");
            dup.put("checkTime", prev != null && prev.getCheckTime() != null ? prev.getCheckTime().format(DT) : null);
            dup.put("gate", prev != null ? prev.getGate() : null);
            throw new StatusCodeException(StatusCode.TICKET_ALREADY_USED, "该票已核销入场", dup);
        }

        ticket.setVerifyStatus(1);
        ticket.setVerifyTime(now);
        if (gate != null) {
            ticket.setGate(gate);
        }
        electronicTicketMapper.updateById(ticket);

        com.livehouse.entity.User u = userService.getById(operatorUserId);
        String operatorName = u != null && u.getNickName() != null ? u.getNickName() : "工作人员";

        CheckInRecord record = new CheckInRecord();
        record.setTicketId(ticket.getId());
        record.setUserId(ticket.getUserId());
        record.setShowId(showId);
        record.setOperatorId(operatorUserId);
        record.setOperatorName(operatorName);
        record.setGate(gate);
        record.setResult(1);
        record.setCheckTime(now);
        checkInRecordService.save(record);
        creditAccountService.grantPendingAfterVerify(ticket.getId());

        if (ticketCode != null && !ticketCode.isEmpty()) {
            stringRedisTemplate.delete("ticket:present:" + ticketCode);
        }

        Performance perf = show.getPerformanceId() != null ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(ticket.getTicketTypeId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", "SUCCESS");
        data.put("resultText", "核销成功");
        data.put("ticketId", String.valueOf(ticket.getId()));
        data.put("displayCode", ticket.getDisplayCode());
        data.put("title", perf != null ? perf.getTitle() : show.getTitle());
        data.put("ticketTypeName", tt == null ? "" : tt.getName());
        data.put("audienceName", ticket.getAudienceName());
        data.put("gate", gate);
        data.put("checkTime", now.format(DT));
        data.put("operatorName", operatorName);
        return data;
    }

    @Override
    public Map<String, Object> stats(Long userId, Long showId) {
        Show show = showService.getById(showId);
        if (show == null) {
            throw new StatusCodeException(StatusCode.SESSION_NOT_FOUND);
        }
        venueStaffService.requireStaffAtVenue(userId, show.getVenueId());

        long totalTickets = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getShowId, showId)
                .eq(TicketOrder::getPayStatus, 1))
                .stream().mapToInt(o -> o.getQuantity() == null ? 0 : o.getQuantity()).sum();
        long verifiedCount = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getShowId, showId)
                .eq(ElectronicTicket::getVerifyStatus, 1));

        List<CheckInRecord> recent = checkInRecordService.lambdaQuery()
                .eq(CheckInRecord::getShowId, showId)
                .orderByDesc(CheckInRecord::getCheckTime)
                .last("LIMIT 10")
                .list();

        List<Map<String, Object>> recentRecords = recent.stream().map(r -> {
            ElectronicTicket t = electronicTicketMapper.selectById(r.getTicketId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ticketId", String.valueOf(r.getTicketId()));
            m.put("shortCode", t == null ? null : t.getShortCode());
            m.put("audienceName", t == null ? null : t.getAudienceName());
            m.put("gate", r.getGate());
            m.put("checkTime", r.getCheckTime() == null ? null : r.getCheckTime().format(DT));
            m.put("operatorName", r.getOperatorName());
            m.put("result", r.getResult() != null && r.getResult() == 1 ? "OK" : "DUPLICATE");
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalTickets", totalTickets);
        data.put("verifiedCount", verifiedCount);
        data.put("pendingCount", Math.max(0, totalTickets - verifiedCount));
        data.put("verifyRate", totalTickets == 0 ? 0 : Math.round(verifiedCount * 1000.0 / totalTickets) / 10.0);
        data.put("recentRecords", recentRecords);
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
        if (shortCode != null && !shortCode.isEmpty()) {
            return electronicTicketMapper.selectOne(new LambdaQueryWrapper<ElectronicTicket>()
                    .eq(ElectronicTicket::getShowId, showId)
                    .eq(ElectronicTicket::getShortCode, shortCode)
                    .last("LIMIT 1"));
        }
        return null;
    }

    private void recordFail(ElectronicTicket ticket, Long showId, Long operatorId, String gate, String reason) {
        if (ticket == null) {
            return;
        }
        CheckInRecord record = new CheckInRecord();
        record.setTicketId(ticket.getId());
        record.setUserId(ticket.getUserId());
        record.setShowId(showId);
        record.setOperatorId(operatorId);
        record.setGate(gate);
        record.setResult(0);
        record.setFailReason(reason);
        record.setCheckTime(LocalDateTime.now());
        checkInRecordService.save(record);
    }
}