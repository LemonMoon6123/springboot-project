package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Message;
import com.livehouse.mapper.MessageMapper;
import com.livehouse.service.app.AppMessageService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppMessageServiceImpl implements AppMessageService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MessageMapper messageMapper;

    @Override
    public Map<String, Object> list(Long userId, String type, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        LambdaQueryWrapper<Message> w = new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .orderByDesc(Message::getCreateTime);
        if (type != null && !type.isEmpty() && !"all".equalsIgnoreCase(type)) {
            w.eq(Message::getType, type);
        }
        long total = messageMapper.selectCount(w);
        List<Message> rows = messageMapper.selectList(w.last("LIMIT " + (current - 1) * size + "," + size));
        List<Map<String, Object>> records = rows.stream().map(this::toRecord).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("pages", total == 0 ? 0 : (total + size - 1) / size);
        data.put("unread", unreadBreakdown(userId));
        return data;
    }

    @Override
    public void readAll(Long userId) {
        List<Message> unread = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
        LocalDateTime now = LocalDateTime.now();
        for (Message m : unread) {
            m.setIsRead(1);
            m.setReadTime(now);
            messageMapper.updateById(m);
        }
    }

    @Override
    public int countUnread(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)).intValue();
    }

    @Override
    public void insertMessage(Long userId, String type, String title, String content, String bizType, String bizId) {
        Message m = new Message();
        m.setUserId(userId);
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setBizType(bizType);
        m.setBizId(bizId);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private Map<String, Object> unreadBreakdown(Long userId) {
        long remind = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId).eq(Message::getIsRead, 0).eq(Message::getType, "remind"));
        long order = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId).eq(Message::getIsRead, 0).eq(Message::getType, "order"));
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("remind", remind);
        u.put("order", order);
        u.put("total", remind + order);
        return u;
    }

    private Map<String, Object> toRecord(Message m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(m.getId()));
        r.put("type", m.getType());
        r.put("title", m.getTitle());
        r.put("time", m.getCreateTime() == null ? null : m.getCreateTime().format(DT));
        r.put("content", m.getContent());
        r.put("bizType", m.getBizType());
        r.put("bizId", m.getBizId());
        r.put("unread", m.getIsRead() == null || m.getIsRead() == 0);
        return r;
    }
}