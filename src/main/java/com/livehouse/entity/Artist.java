package com.livehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_artist")
public class Artist implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String avatar;
    private String type;
    private String city;
    private String intro;
    private Integer followCount;
    private LocalDateTime createTime;
}