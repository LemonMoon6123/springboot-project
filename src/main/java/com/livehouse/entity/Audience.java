package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 常用观演人
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_audience")
public class Audience implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /** 证件类型：1身份证 2护照 3港澳通行证 */
    private Integer idType;

    private String idCardEnc;

    private String idCardMask;

    private String idCardHash;

    private String phone;

    private Integer isDefault;

    /** 实名状态：0未认证 1已认证 */
    private Integer authStatus;

    private LocalDateTime lastUsedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}