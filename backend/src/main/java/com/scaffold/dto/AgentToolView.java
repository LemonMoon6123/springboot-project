package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 工具视图")
public class AgentToolView {
    @Schema(description = "工具名称", example = "orderQuery")
    private String name;
    @Schema(description = "工具分组", example = "订单")
    private String groupName;
    @Schema(description = "工具描述", example = "查询用户订单状态")
    private String description;
    @Schema(description = "是否需要登录", example = "true")
    private boolean needLogin;
    @Schema(description = "是否需要确认", example = "false")
    private boolean confirmRequired;
    @Schema(description = "参数列表")
    private List<ParamView> params = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Agent 工具参数")
    public static class ParamView {
        @Schema(description = "参数名", example = "userId")
        private String name;
        @Schema(description = "参数类型", example = "Long")
        private String type;
        @Schema(description = "参数描述", example = "用户ID")
        private String description;
        @Schema(description = "是否必填", example = "true")
        private boolean required;
    }
}
