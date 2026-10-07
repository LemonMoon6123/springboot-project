package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "物流节点")
public class LogisticsNode {
    /** created / paid / shipped / collected / transit / delivering / signed */
    @Schema(description = "节点编码:created/paid/shipped/collected/transit/delivering/signed", example = "shipped")
    private String code;
    @Schema(description = "节点标题", example = "已发货")
    private String title;
    @Schema(description = "节点描述", example = "商家已发货,等待揽收")
    private String desc;
    @Schema(description = "节点时间", example = "2026-09-23 10:00:00")
    private String time;
    /** 是否已完成 */
    @Schema(description = "是否已完成", example = "true")
    private Boolean done;
    /** 是否当前节点 */
    @Schema(description = "是否当前节点", example = "false")
    private Boolean current;
}
