package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属商家/店铺 ID（与 /user/shop/:id 一致） */
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String cover;
    /** JSON 字符串 */
    private String images;
    private Double price;
    private Double originalPrice;
    private Integer stock;
    private Integer sales;
    /** 商品简介（列表/卡片短文案） */
    private String description;
    /** 商品详情（富文本 HTML） */
    private String detail;
    /** 0下架 1上架 */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;

    /** 店铺名称（非表字段） */
    @TableField(exist = false)
    private String shopName;
    /** 店铺头像（非表字段） */
    @TableField(exist = false)
    private String shopAvatar;
    /** 分类名称（非表字段） */
    @TableField(exist = false)
    private String categoryName;
}
