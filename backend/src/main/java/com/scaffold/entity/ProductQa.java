package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_qa")
public class ProductQa {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long userId;
    private String question;
    private String answer;
    private Long answerBy;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;
    private String answeredAt;

    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String productCover;
}
