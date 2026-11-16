package com.livehouse.dto.app;

import lombok.Data;

@Data
public class AudienceCreateRequest {
    private String name;
    private Integer idType;
    private String idCard;
    private String phone;
}