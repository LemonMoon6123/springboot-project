package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.Performance;
import com.livehouse.entity.Show;
import com.livehouse.entity.TicketType;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.mapper.PerformanceMapper;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.service.app.AppTicketService;
import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AppTicketServiceImpl implements AppTicketService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHOW_TEXT = DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA);
    private static final int PRESENT_TTL_SECONDS = 65;

    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private IShowService showService;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private ITicketTypeService ticketTypeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Object> wallet(Long userId, String status, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);

        LambdaQueryWrapper<ElectronicTicket> q = new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getUserId, userId)
                .orderByDesc(ElectronicTicket::getCreateTime);
        if ("valid".equals(status)) {
            q.eq(ElectronicTicket::getVerifyStatus, 0);
        } else if ("invalid".equals(status)) {
            q.ne(ElectronicTicket::getVerifyStatus, 0);
        }

        Page<ElectronicTicket> page = electronicTicketMapper.selectPage(new Page<>(current, size), q);
        long validCount = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getUserId, userId).eq(ElectronicTicket::getVerifyStatus, 0));

        List<Map<String, Object>> records = page.getRecords().stream().map(this::toWalletItem).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("validCount", validCount);
        data.put("records", records);
        data.put("total", page.getTotal());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());
        data.put("pages", page.getPages());
        return data;
    }

    @Override
    public Map<String, Object> presentCode(Long userId, Long ticketId) {
        ElectronicTicket ticket = electronicTicketMapper.selectById(ticketId);
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new StatusCodeException(StatusCode.TICKET_NOT_FOUND);
        }
        if (ticket.getVerifyStatus() != null && ticket.getVerifyStatus() != 0) {
            throw new StatusCodeException(StatusCode.TICKET_NOT_VALID);
        }

        String token = UUID.randomUUID().toString(true) + "_" + ticketId;
        String redisKey = "ticket:present:" + token;
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(ticketId), PRESENT_TTL_SECONDS, TimeUnit.SECONDS);

        Show show = showService.getById(ticket.getShowId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;
        String title = perf != null ? perf.getTitle() : (show == null ? "" : show.getTitle());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("presentToken", token);
        data.put("displayCode", ticket.getDisplayCode());
        data.put("expireSeconds", PRESENT_TTL_SECONDS);
        data.put("gate", ticket.getGate());
        data.put("title", title);
        return data;
    }

    private Map<String, Object> toWalletItem(ElectronicTicket t) {
        Show show = showService.getById(t.getShowId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(t.getTicketTypeId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ticketId", String.valueOf(t.getId()));
        m.put("displayCode", t.getDisplayCode());
        m.put("shortCode", t.getShortCode());
        m.put("title", perf != null ? perf.getTitle() : (show == null ? "" : show.getTitle()));
        m.put("cover", perf != null ? perf.getPoster() : (show == null ? null : show.getImage()));
        if (show != null && show.getStartTime() != null) {
            try {
                m.put("showTimeText", show.getStartTime().format(SHOW_TEXT));
            } catch (Exception e) {
                m.put("showTimeText", show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
            }
        }
        String venueName = show == null ? "" : "";
        if (show != null) {
            venueName = show.getTitle();
        }
        m.put("infoText", venueName + " · " + (tt == null ? "" : tt.getName()));
        m.put("audienceName", t.getAudienceName());
        m.put("gate", t.getGate());
        m.put("verifyStatus", t.getVerifyStatus());
        m.put("verifyStatusText", verifyText(t.getVerifyStatus()));
        m.put("canPresent", t.getVerifyStatus() != null && t.getVerifyStatus() == 0);
        m.put("invalidReason", invalidReason(t, show));
        return m;
    }

    private static String verifyText(Integer vs) {
        if (vs == null || vs == 0) return "待核销";
        if (vs == 1) return "已核销";
        return "已失效";
    }

    private static String invalidReason(ElectronicTicket t, Show show) {
        if (t.getVerifyStatus() == null || t.getVerifyStatus() == 0) {
            if (show != null && show.getEndTime() != null && show.getEndTime().isBefore(LocalDateTime.now())) {
                return "演出已结束";
            }
            return null;
        }
        if (t.getVerifyStatus() == 1) return "已核销入场";
        if (t.getVerifyStatus() == 2) return "已退票";
        return "已失效";
    }
}