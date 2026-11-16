package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_real_name_auth")
public class RealNameAuth implements Serializable {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_AUDITING = 1;
    public static final int STATUS_PASS = 2;
    public static final int STATUS_REJECT = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String realName;
    private Integer idType;
    private String idCardEnc;
    private String idCardMask;
    private String phone;
    private Integer authStatus;
    private String authChannel;
    private BigDecimal faceScore;
    private String rejectReason;
    private LocalDateTime authTime;
    private Integer modifyApplyStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}