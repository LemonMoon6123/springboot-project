package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.scaffold.dto.ShopVO;
import lombok.Data;

@Data
@TableName("shop_follow")
public class ShopFollow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long merchantId;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;

    @TableField(exist = false)
    private ShopVO shop;
}
