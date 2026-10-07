package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "店铺信息")
public class ShopVO {

    @Schema(description = "店铺 ID")
    private Long id;

    @Schema(description = "店铺名称", example = "张三数码旗舰店")
    private String shopName;

    @Schema(description = "店铺简介", example = "主营数码电子产品")
    private String shopDesc;

    @Schema(description = "商家头像 URL", example = "/uploads/avatar.jpg")
    private String avatar;

    @Schema(description = "商家昵称", example = "张三")
    private String nickname;

    @Schema(description = "在售商品数", example = "128")
    private Integer productCount;

    @Schema(description = "累计销量", example = "1024")
    private Integer salesCount;

    @Schema(description = "开店时间")
    private String createdAt;
}
