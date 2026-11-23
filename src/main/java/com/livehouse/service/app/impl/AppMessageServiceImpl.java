package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Message;
import com.livehouse.mapper.MessageMapper;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.vo.app.message.MessageItemVo;
import com.livehouse.vo.app.message.MessageListVo;
import com.livehouse.vo.app.message.MessageUnreadVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppMessageServiceImpl implements AppMessageService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MessageMapper messageMapper;

    @Override
    public MessageListVo list(Long userId, String type, int current, int size) {
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
        List<MessageItemVo> records = rows.stream().map(this::toRecord).collect(Collectors.toList());

        MessageListVo data = new MessageListVo();
        data.setRecords(records);
        data.setTotal(total);
        data.setCurrent(current);
        data.setSize(size);
        data.setPages(total == 0 ? 0 : (total + size - 1) / size);
        data.setUnread(unreadBreakdown(userId));
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

    private MessageUnreadVo unreadBreakdown(Long userId) {
        long remind = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId).eq(Message::getIsRead, 0).eq(Message::getType, "remind"));
        long order = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId).eq(Message::getIsRead, 0).eq(Message::getType, "order"));
        MessageUnreadVo u = new MessageUnreadVo();
        u.setRemind(remind);
        u.setOrder(order);
        u.setTotal(remind + order);
        return u;
    }

    private MessageItemVo toRecord(Message m) {
        MessageItemVo r = new MessageItemVo();
        r.setId(String.valueOf(m.getId()));
        r.setType(m.getType());
        r.setTitle(m.getTitle());
        r.setTime(m.getCreateTime() == null ? null : m.getCreateTime().format(DT));
        r.setContent(m.getContent());
        r.setBizType(m.getBizType());
        r.setBizId(m.getBizId());
        r.setUnread(m.getIsRead() == null || m.getIsRead() == 0);
        return r;
    }
}