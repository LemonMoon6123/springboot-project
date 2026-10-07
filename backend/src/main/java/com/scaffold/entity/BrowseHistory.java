package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("browse_history")
public class BrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private String viewedAt;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;

    @TableField(exist = false)
    private Product product;
}
