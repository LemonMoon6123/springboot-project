package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.mapper.ShowCommentLikeMapper;
import com.livehouse.mapper.ShowCommentMapper;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.service.IUserService;
import com.livehouse.service.app.AppCommentService;
import com.livehouse.vo.app.comment.CommentItemVo;
import com.livehouse.vo.app.comment.CommentLikeVo;
import com.livehouse.vo.app.comment.CommentListVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private ShowCommentLikeMapper showCommentLikeMapper;
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private IUserService userService;

    @Override
    public CommentListVo listComments(Long showId, String filter, int current, int size, Long userId) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);
        LambdaQueryWrapper<ShowComment> w = new LambdaQueryWrapper<ShowComment>()
                .eq(ShowComment::getShowId, showId)
                .eq(ShowComment::getStatus, 1);
        if ("featured".equalsIgnoreCase(filter)) {
            w.eq(ShowComment::getIsFeatured, 1);
            w.orderByDesc(true, ShowComment::getLikeCount);
        } else if ("newest".equalsIgnoreCase(filter)) {
            w.orderByDesc(true, ShowComment::getCreateTime);
        } else {
            w.orderByDesc(true, ShowComment::getLikeCount);
        }

        long total = showCommentMapper.selectCount(w);
        List<ShowComment> rows = showCommentMapper.selectList(w.last("LIMIT " + (current - 1) * size + "," + size));
        Set<Long> likedIds = likedCommentIds(userId, rows);
        List<CommentItemVo> records = rows.stream().map(c -> {
            CommentItemVo r = new CommentItemVo();
            r.setId(String.valueOf(c.getId()));
            r.setUserName(c.getUserName());
            r.setUserIcon(null);
            r.setRating(c.getRating());
            r.setContent(c.getContent());
            r.setImages(c.getImages());
            int likeCount = c.getLikeCount() == null ? 0 : c.getLikeCount();
            r.setLikeCount(likeCount);
            r.setLiked(likedIds.contains(c.getId()));
            r.setCreateTime(c.getCreateTime() == null ? null : c.getCreateTime().format(DT));
            return r;
        }).collect(Collectors.toList());
        CommentListVo data = new CommentListVo();
        data.setRecords(records);
        data.setTotal(total);
        data.setCurrent(current);
        data.setSize(size);
        data.setPages(total == 0 ? 0 : (total + size - 1) / size);
        return data;
    }

    /**
     * orderId用于检查用户是否已支付并且已核销电子票，否则无法评论。
     * @param userId
     * @param showId
     * @param orderId
     * @param rating
     * @param content
     * @param imagesJson
     */
    @Override
    public void postComment(Long userId, Long showId, Long orderId, Integer rating, String content, String imagesJson) {
        if (showId == null || orderId == null || content == null || content.trim().isEmpty()) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId) || order.getPayStatus() != 1) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        if (order.getShowId() == null || !order.getShowId().equals(showId)) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "订单与场次不匹配");
        }
        long verified = electronicTicketMapper.selectCount(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId)
                .eq(ElectronicTicket::getVerifyStatus, 1));
        if (verified == 0) {
            throw new StatusCodeException(StatusCode.ORDER_STATUS_ILLEGAL, "观演核销后方可评价");
        }
        // 只能评论一次
        if (hasCommentForOrder(orderId)) {
            throw new StatusCodeException(StatusCode.CONFLICT, "该订单已评价");
        }
        User user = userService.getById(userId);
        ShowComment c = new ShowComment();
        c.setShowId(showId);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentLikeVo toggleCommentLike(Long userId, Long commentId) {
        if (userId == null || commentId == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        ShowComment comment = showCommentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == null || comment.getStatus() != 1) {
            throw new StatusCodeException(StatusCode.NOT_FOUND, "评论不存在");
        }
        ShowCommentLike existing = showCommentLikeMapper.selectOne(new LambdaQueryWrapper<ShowCommentLike>()
                .eq(ShowCommentLike::getCommentId, commentId)
                .eq(ShowCommentLike::getUserId, userId));
        boolean liked;
        if (existing != null) {
            showCommentLikeMapper.deleteById(existing.getId());
            int next = Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1);
            showCommentMapper.update(null, new LambdaUpdateWrapper<ShowComment>()
                    .eq(ShowComment::getId, commentId)
                    .set(ShowComment::getLikeCount, next));
            liked = false;
            comment.setLikeCount(next);
        } else {
            ShowCommentLike like = new ShowCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setCreateTime(java.time.LocalDateTime.now());
            showCommentLikeMapper.insert(like);
            int next = (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + 1;
            showCommentMapper.update(null, new LambdaUpdateWrapper<ShowComment>()
                    .eq(ShowComment::getId, commentId)
                    .set(ShowComment::getLikeCount, next));
            liked = true;
            comment.setLikeCount(next);
        }
        CommentLikeVo data = new CommentLikeVo();
        data.setCommentId(String.valueOf(commentId));
        data.setLiked(liked);
        data.setLikeCount(comment.getLikeCount());
        return data;
    }

    private Set<Long> likedCommentIds(Long userId, List<ShowComment> rows) {
        if (userId == null || rows.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ids = rows.stream().map(ShowComment::getId).collect(Collectors.toList());
        return showCommentLikeMapper.selectList(new LambdaQueryWrapper<ShowCommentLike>()
                        .eq(ShowCommentLike::getUserId, userId)
                        .in(ShowCommentLike::getCommentId, ids))
                .stream()
                .map(ShowCommentLike::getCommentId)
                .collect(Collectors.toSet());
    }
}