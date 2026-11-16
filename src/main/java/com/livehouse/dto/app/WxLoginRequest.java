package com.livehouse.dto.app;

import lombok.Data;

@Data
public class WxLoginRequest {
    private String code;
    private String encryptedData;
    private String iv;
}