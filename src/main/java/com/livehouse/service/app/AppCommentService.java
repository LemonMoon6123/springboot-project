package com.livehouse.service.app;

import com.livehouse.vo.app.comment.CommentLikeVo;
import com.livehouse.vo.app.comment.CommentListVo;

public interface AppCommentService {

    CommentListVo listComments(Long showId, String filter, int current, int size, Long userId);

    void postComment(Long userId, Long showId, Long orderId, Integer rating, String content, String imagesJson);

    boolean hasCommentForOrder(Long orderId);

    CommentLikeVo toggleCommentLike(Long userId, Long commentId);
}