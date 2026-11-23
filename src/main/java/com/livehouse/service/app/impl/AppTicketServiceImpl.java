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
import com.livehouse.vo.app.ticket.TicketPresentVo;
import com.livehouse.vo.app.ticket.TicketWalletItemVo;
import com.livehouse.vo.app.ticket.TicketWalletVo;
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
    public TicketWalletVo wallet(Long userId, String status, int current, int size) {
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

        List<TicketWalletItemVo> records = page.getRecords().stream().map(this::toWalletItem).collect(Collectors.toList());

        TicketWalletVo data = new TicketWalletVo();
        data.setValidCount(validCount);
        data.setRecords(records);
        data.setTotal(page.getTotal());
        data.setCurrent((int) page.getCurrent());
        data.setSize((int) page.getSize());
        data.setPages(page.getPages());
        return data;
    }

    @Override
    public TicketPresentVo presentCode(Long userId, Long ticketId) {
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

        TicketPresentVo data = new TicketPresentVo();
        data.setPresentToken(token);
        data.setDisplayCode(ticket.getDisplayCode());
        data.setShortCode(ticket.getShortCode());
        data.setExpireSeconds(PRESENT_TTL_SECONDS);
        data.setGate(ticket.getGate());
        data.setTitle(title);
        return data;
    }

    private TicketWalletItemVo toWalletItem(ElectronicTicket t) {
        Show show = showService.getById(t.getShowId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(t.getTicketTypeId());

        TicketWalletItemVo m = new TicketWalletItemVo();
        m.setTicketId(String.valueOf(t.getId()));
        m.setDisplayCode(t.getDisplayCode());
        m.setShortCode(t.getShortCode());
        m.setTitle(perf != null ? perf.getTitle() : (show == null ? "" : show.getTitle()));
        m.setCover(perf != null ? perf.getPoster() : (show == null ? null : show.getImage()));
        if (show != null && show.getStartTime() != null) {
            try {
                m.setShowTimeText(show.getStartTime().format(SHOW_TEXT));
            } catch (Exception e) {
                m.setShowTimeText(show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
            }
        }
        String venueName = show == null ? "" : "";
        if (show != null) {
            venueName = show.getTitle();
        }
        m.setInfoText(venueName + " · " + (tt == null ? "" : tt.getName()));
        m.setAudienceName(t.getAudienceName());
        m.setGate(t.getGate());
        m.setVerifyStatus(t.getVerifyStatus());
        m.setVerifyStatusText(verifyText(t.getVerifyStatus()));
        m.setCanPresent(t.getVerifyStatus() != null && t.getVerifyStatus() == 0);
        m.setInvalidReason(invalidReason(t, show));
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