package com.livehouse.dto.app;

import lombok.Data;

@Data
public class FavoriteTargetRequest {

    private String targetType;
    private String targetId;
}