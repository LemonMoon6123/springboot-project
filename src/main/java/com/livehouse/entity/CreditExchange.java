package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_exchange")
public class CreditExchange implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long goodsId;
  private Integer creditCost;
  private String redeemCode;
  private Integer status;
  private String idempotentKey;
  private LocalDateTime createTime;
  private LocalDateTime redeemTime;
}