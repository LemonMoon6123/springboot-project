package com.scaffold.dto;

import com.scaffold.entity.CartItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ShopOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导购响应")
public class AiGuideResponse {

    @Schema(description = "AI 回复文本")
    private String reply;

    @Schema(description = "推荐商品列表")
    private List<Product> products;

    /** Agent：相关购物车项 */
    @Schema(description = "Agent:相关购物车项")
    private List<CartItem> cartItems;

    /** Agent：相关订单 */
    @Schema(description = "Agent:相关订单")
    private List<ShopOrder> orders;

    /** 前端快捷入口，如去购物车/订单/登录 */
    @Schema(description = "前端快捷入口,如去购物车/订单/登录")
    private List<Map<String, String>> actions;

    /** 需要登录才能继续 */
    @Schema(description = "是否需要登录才能继续操作")
    private boolean needLogin;

    public AiGuideResponse(String reply, List<Product> products) {
        this.reply = reply;
        this.products = products;
    }

    public static AiGuideResponse of(String reply, List<Product> products) {
        return new AiGuideResponse(reply, products);
    }

    public void addAction(String type, String label, String path) {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        for (Map<String, String> a : actions) {
            if (path.equals(a.get("path"))) {
                return;
            }
        }
        Map<String, String> action = new LinkedHashMap<>();
        action.put("type", type);
        action.put("label", label);
        action.put("path", path);
        actions.add(action);
    }
}
