package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer status;
    private String shopName;
    private String shopDesc;
    /** 营业执照图片 */
    private String licenseImage;
    /** 身份证正面 */
    private String idCardFront;
    /** 身份证反面 */
    private String idCardBack;
    /** 0待审核 1通过 2拒绝 */
    private Integer auditStatus;
    private String auditRemark;
    @TableField(fill = FieldFill.INSERT)
    private String createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;
}
