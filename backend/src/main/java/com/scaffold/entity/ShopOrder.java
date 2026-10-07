package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("shop_order")
public class ShopOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    /** 地址快照 JSON */
    private String addressSnapshot;
    private Double totalAmount;
    /** 0待支付 1已支付/待发货 2已发货 3已完成 4已取消 */
    private Integer status;
    private String payTime;
    private String shipTime;
    private String finishTime;
    /** 快递公司 */
    private String expressCompany;
    /** 物流单号 */
    private String trackingNo;
    /** 物流时间轴 JSON */
    private String logisticsJson;
    /** 0无 1申请中 2已同意 3已拒绝 */
    private Integer afterSaleStatus;
    /** 1仅退款 2退货退款 3换货 */
    private Integer afterSaleType;
    private String afterSaleReason;
    private String afterSaleReply;
    private String afterSaleAt;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;

    @TableField(exist = false)
    private List<OrderItem> items;
    /** 店铺名称（商家 shopName） */
    @TableField(exist = false)
    private String shopName;
    /** 店铺头像 */
    @TableField(exist = false)
    private String shopAvatar;
    /** 买家用户名 */
    @TableField(exist = false)
    private String buyerUsername;
    /** 买家昵称 */
    @TableField(exist = false)
    private String buyerNickname;
    /** 买家手机 */
    @TableField(exist = false)
    private String buyerPhone;
    /** 买家头像 */
    @TableField(exist = false)
    private String buyerAvatar;
    /** 物流时间轴 */
    @TableField(exist = false)
    private java.util.List<com.scaffold.dto.LogisticsNode> logisticsTrace;
    /** 订单是否已全部评价 */
    @TableField(exist = false)
    private Boolean reviewed;
    /** 是否还能评价（已完成且仍有未评价商品） */
    @TableField(exist = false)
    private Boolean canReview;
    /** 是否可申请售后（已发货/已完成且无进行中售后） */
    @TableField(exist = false)
    private Boolean canApplyAfterSale;
}
