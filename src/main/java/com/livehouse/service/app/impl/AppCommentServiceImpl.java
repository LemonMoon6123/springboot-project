package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.mapper.ShowCommentMapper;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.service.IUserService;
import com.livehouse.service.app.AppCommentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppCommentServiceImpl implements AppCommentService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ShowCommentMapper showCommentMapper;
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private IUserService userService;

    @Override
    public Map<String, Object> listComments(Long performanceId, String filter, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        LambdaQueryWrapper<ShowComment> w = new LambdaQueryWrapper<ShowComment>()
                .eq(ShowComment::getPerformanceId, performanceId)
                .eq(ShowComment::getStatus, 1);
        if ("featured".equalsIgnoreCase(filter)) {
            w.eq(ShowComment::getIsFeatured, 1);
            w.orderByDesc(ShowComment::getCreateTime);
        } else if ("newest".equalsIgnoreCase(filter)) {
            w.orderByDesc(ShowComment::getCreateTime);
        } else {
            w.orderByDesc(ShowComment::getIsFeatured).orderByDesc(ShowComment::getCreateTime);
        }
        long total = showCommentMapper.selectCount(w);
        List<ShowComment> rows = showCommentMapper.selectList(w.last("LIMIT " + (current - 1) * size + "," + size));
        List<Map<String, Object>> records = rows.stream().map(c -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", String.valueOf(c.getId()));
            r.put("userName", c.getUserName());
            r.put("userIcon", null);
            r.put("rating", c.getRating());
            r.put("content", c.getContent());
            r.put("images", c.getImages());
            r.put("likeCount", c.getLikeCount());
            r.put("liked", false);
            r.put("createTime", c.getCreateTime() == null ? null : c.getCreateTime().format(DT));
            return r;
        }).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("pages", total == 0 ? 0 : (total + size - 1) / size);
        return data;
    }

    @Override
    public void postComment(Long userId, Long performanceId, Long orderId, Integer rating, String content, String imagesJson) {
        if (orderId == null || content == null || content.trim().isEmpty()) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId) || order.getPayStatus() != 1) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        long verified = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId)
                .eq(ElectronicTicket::getVerifyStatus, 1));
        if (verified == 0) {
            throw new StatusCodeException(StatusCode.ORDER_STATUS_ILLEGAL, "观演核销后方可评价");
        }
        if (hasCommentForOrder(orderId)) {
            throw new StatusCodeException(StatusCode.CONFLICT, "该订单已评价");
        }
        User user = userService.getById(userId);
        ShowComment c = new ShowComment();
        c.setPerformanceId(performanceId);
        c.setOrderId(orderId);
        c.setUserId(userId);
        c.setUserName(user == null ? "用户" : user.getNickName());
        c.setRating(rating == null ? 5 : Math.min(5, Math.max(1, rating)));
        c.setContent(content.trim());
        c.setImages(imagesJson);
        c.setLikeCount(0);
        c.setIsFeatured(0);
        c.setStatus(1);
        c.setCreateTime(java.time.LocalDateTime.now());
        showCommentMapper.insert(c);
    }

    @Override
    public boolean hasCommentForOrder(Long orderId) {
        return showCommentMapper.selectCount(new LambdaQueryWrapper<ShowComment>()
                .eq(ShowComment::getOrderId, orderId)) > 0;
    }
}