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
  private String type;
  /** 0无效 1有效 */
  private Integer status;
  private String bizId;
  private String remark;
  private LocalDateTime createTime;
}