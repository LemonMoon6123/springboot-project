package com.scaffold.agent;

import com.scaffold.entity.CartItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ShopOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 单次请求上下文（ThreadLocal），供 Tool 读写用户身份与前端展示数据。
 */
public final class AgentContext {

    private static final ThreadLocal<AgentContext> HOLDER = new ThreadLocal<>();

    private Long userId;
    private final Map<Long, Product> products = new LinkedHashMap<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    private final List<ShopOrder> orders = new ArrayList<>();
    private final List<Map<String, String>> actions = new ArrayList<>();
    private boolean needLogin;

    private AgentContext() {
    }

    public static AgentContext open(Long userId) {
        AgentContext ctx = new AgentContext();
        ctx.userId = userId;
        HOLDER.set(ctx);
        return ctx;
    }

    public static AgentContext current() {
        AgentContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("AgentContext 未初始化");
        }
        return ctx;
    }

    public static AgentContext currentOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isLoggedIn() {
        return userId != null;
    }

    public void markNeedLogin() {
        this.needLogin = true;
        addAction("login", "去登录", "/login");
    }

    public boolean isNeedLogin() {
        return needLogin;
    }

    public void addProducts(List<Product> list) {
        if (list == null) {
            return;
        }
        for (Product p : list) {
            if (p != null && p.getId() != null) {
                products.put(p.getId(), p);
            }
        }
    }

    public void addProduct(Product product) {
        if (product != null && product.getId() != null) {
            products.put(product.getId(), product);
        }
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products.values());
    }

    public void setCartItems(List<CartItem> items) {
        cartItems.clear();
        if (items != null) {
            cartItems.addAll(items);
        }
        if (items != null && !items.isEmpty()) {
            addAction("cart", "查看购物车", "/user/cart");
        }
    }

    public List<CartItem> getCartItems() {
        return cartItems;
    }

    public void setOrders(List<ShopOrder> list) {
        orders.clear();
        if (list != null) {
            orders.addAll(list);
        }
        if (list != null && !list.isEmpty()) {
            addAction("orders", "我的订单", "/user/orders");
        }
    }

    public void addOrder(ShopOrder order) {
        if (order != null) {
            orders.add(order);
            addAction("orders", "我的订单", "/user/orders");
            if (order.getId() != null) {
                addAction("order", "我的订单", "/user/orders");
            }
        }
    }

    public List<ShopOrder> getOrders() {
        return orders;
    }

    public void addAction(String type, String label, String path) {
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

    public List<Map<String, String>> getActions() {
        return actions;
    }
}
