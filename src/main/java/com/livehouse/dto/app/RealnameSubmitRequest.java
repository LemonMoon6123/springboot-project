package com.livehouse.dto.app;

import lombok.Data;

@Data
public class RealnameSubmitRequest {

    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
}