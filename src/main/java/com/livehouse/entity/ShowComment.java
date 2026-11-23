package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_show_comment")
public class ShowComment implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long showId;
  private Long orderId;
  private Long userId;
  private String userName;
  private Integer rating;
  private String content;
  private String images;
  private Integer likeCount;
  private Integer isFeatured;
  private Integer status;
  private LocalDateTime createTime;
}