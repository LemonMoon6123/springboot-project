package com.livehouse.dto.app;

import lombok.Data;

import java.util.List;

@Data
public class CommentPostRequest {

    private Long orderId;
    private Integer rating;
    private String content;
    private List<String> images;
}