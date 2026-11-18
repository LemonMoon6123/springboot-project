package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_goods")
public class CreditGoods implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;
  private String name;
  private String icon;
  private String image;
  private Integer creditPrice;
  private String goodsType;
  private Integer stock;
  private Integer perUserLimit;
  private Integer status;
  private LocalDateTime createTime;
}