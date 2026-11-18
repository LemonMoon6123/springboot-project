package com.livehouse.dto.app;

import lombok.Data;

@Data
public class BindPhoneRequest {
    private String phone;
    private String code;
    private String tempToken;
}