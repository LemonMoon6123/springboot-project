package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_log")
public class CreditLog implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Integer changeAmount;
  private Integer balance;
  private String type;
  /** 0待发放 1已发放 2已过期 */
  private Integer status;
  private LocalDateTime releaseTime;
  private LocalDateTime expireTime;
  private String bizId;
  private String remark;
  private LocalDateTime createTime;
}