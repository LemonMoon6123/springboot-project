package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_message")
public class Message implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private String type;
  private String title;
  private String content;
  private String bizType;
  private String bizId;
  private Integer isRead;
  private LocalDateTime readTime;
  private LocalDateTime createTime;
}