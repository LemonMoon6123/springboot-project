package com.livehouse.dto.app;

import lombok.Data;

@Data
public class AudienceCreateRequest {
    private String name;
    private Integer idType;
    private String idCard;
    private String phone;
    /** 为 true 时使用账号实名信息填充证件（须已通过实名认证） */
    private Boolean useAccountRealName;
}