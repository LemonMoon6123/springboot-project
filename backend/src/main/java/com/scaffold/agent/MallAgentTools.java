package com.scaffold.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.common.BusinessException;
import com.scaffold.dto.CreateOrderRequest;
import com.scaffold.entity.CartItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ShopOrder;
import com.scaffold.entity.UserAddress;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.service.AddressService;
import com.scaffold.service.CartService;
import com.scaffold.service.OrderService;
import com.scaffold.service.ProductService;
import com.scaffold.service.RecommendService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring AI Agent Tools：导购检索、购物车、地址、下单与支付。
 */
@Component
public class MallAgentTools {

    private static final Logger log = LoggerFactory.getLogger(MallAgentTools.class);

    /** 用户口语 / 同义词 → 可命中站内商品的检索词 */
    private static final Map<String, List<String>> SEARCH_ALIASES = new LinkedHashMap<>();
    private static final List<String> STOP_WORDS = List.of(
            "帮我", "我想", "我要", "给我", "推荐", "介绍", "一个", "一件", "一些", "一下", "看看",
            "找找", "找个", "找一下", "有没有", "什么", "怎么", "哪个", "适合", "可以", "来点", "来个",
            "穿搭", "好物", "商品", "东西", "款式", "同款", "类似", "左右", "以内", "以下", "附近",
            "预算", "大概", "差不多", "谢谢", "请问"
    );

    static {
        SEARCH_ALIASES.put("耳机", List.of("耳机", "降噪", "蓝牙", "无线"));
        SEARCH_ALIASES.put("卫衣", List.of("卫衣", "棉", "服饰"));
        SEARCH_ALIASES.put("衣服", List.of("卫衣", "服饰", "棉"));
        SEARCH_ALIASES.put("服饰", List.of("卫衣", "服饰"));
        SEARCH_ALIASES.put("外套", List.of("卫衣", "服饰"));
        SEARCH_ALIASES.put("台灯", List.of("台灯", "氛围", "灯", "护眼"));
        SEARCH_ALIASES.put("灯", List.of("台灯", "灯", "氛围"));
        SEARCH_ALIASES.put("笔记本", List.of("笔记本", "电脑", "轻薄"));
        SEARCH_ALIASES.put("电脑", List.of("电脑", "笔记本"));
        SEARCH_ALIASES.put("手表", List.of("手表", "智能表", "心率"));
        SEARCH_ALIASES.put("洁面", List.of("洁面", "氨基酸", "护肤"));
        SEARCH_ALIASES.put("护肤", List.of("洁面", "护肤", "氨基酸"));
        SEARCH_ALIASES.put("咖啡", List.of("咖啡", "挂耳", "礼盒"));
        SEARCH_ALIASES.put("运动鞋", List.of("运动鞋", "跑步", "鞋"));
        SEARCH_ALIASES.put("跑步", List.of("跑步", "运动鞋", "鞋"));
        SEARCH_ALIASES.put("鞋", List.of("鞋", "运动鞋", "跑步"));
        SEARCH_ALIASES.put("hoodie", List.of("卫衣"));
        SEARCH_ALIASES.put("earphone", List.of("耳机"));
        SEARCH_ALIASES.put("headphone", List.of("耳机"));
        SEARCH_ALIASES.put("laptop", List.of("笔记本", "电脑"));
    }

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductService productService;
    @Autowired
    private RecommendService recommendService;
    @Autowired
    private CartService cartService;
    @Autowired
    private AddressService addressService;
    @Autowired
    private OrderService orderService;

    @Tool(description = "按关键词/品类搜索站内在售商品。keyword 请填商品核心词（如：卫衣、耳机、台灯、笔记本），不要填整句「帮我推荐」。可传预算上限 maxPrice。返回商品 id、名称、价格、库存摘要。")
    public String searchProducts(
            @ToolParam(description = "搜索关键词，如耳机、卫衣、台灯；也可传用户原话，工具会自动提取核心词") String keyword,
            @ToolParam(description = "最高价格，可选，单位元") Double maxPrice,
            @ToolParam(description = "返回条数，默认6，最大12") Integer limit) {
        int lim = limit == null ? 6 : Math.max(1, Math.min(limit, 12));
        String raw = keyword == null ? "" : keyword.trim();
        List<String> tokens = expandSearchTokens(raw);
        log.info("Agent searchProducts raw='{}' tokens={} maxPrice={}", raw, tokens, maxPrice);

        List<Product> onShelf = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales)
                .last("LIMIT 200"));
        productService.enrich(onShelf);

        List<Product> matched = filterProducts(onShelf, tokens, maxPrice, lim);

        // SQL LIKE 兜底（避免内存匹配漏掉）
        if (matched.isEmpty() && !tokens.isEmpty()) {
            LinkedHashMap<Long, Product> byId = new LinkedHashMap<>();
            for (String token : tokens) {
                if (token.length() < 1) {
                    continue;
                }
                List<Product> hit = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .and(w -> w.like(Product::getName, token)
                                .or().like(Product::getDescription, token))
                        .orderByDesc(Product::getSales)
                        .last("LIMIT 20"));
                for (Product p : hit) {
                    if (maxPrice != null && p.getPrice() != null && p.getPrice() > maxPrice) {
                        continue;
                    }
                    byId.put(p.getId(), p);
                }
            }
            if (!byId.isEmpty()) {
                matched = new ArrayList<>(byId.values());
                productService.enrich(matched);
                matched = matched.stream()
                        .sorted(Comparator
                                .comparingInt((Product p) -> score(p, tokens)).reversed()
                                .thenComparing(p -> p.getSales() == null ? 0 : p.getSales(), Comparator.reverseOrder()))
                        .limit(lim)
                        .collect(Collectors.toList());
            }
        }

        if (matched.isEmpty() && tokens.isEmpty()) {
            matched = onShelf.stream()
                    .filter(p -> maxPrice == null || (p.getPrice() != null && p.getPrice() <= maxPrice))
                    .limit(lim)
                    .collect(Collectors.toList());
        }

        AgentContext.current().addProducts(matched);
        if (matched.isEmpty()) {
            return "未找到匹配的在售商品。请换更短的核心词再试，例如：耳机、卫衣、台灯、笔记本、洁面、咖啡、运动鞋、手表。";
        }
        return summarizeProducts(matched);
    }

    @Tool(description = "获取个性化或热销推荐商品。登录用户优先协同过滤推荐。")
    public String recommendProducts(
            @ToolParam(description = "可选：参考商品ID，做相似推荐") Long productId,
            @ToolParam(description = "返回条数，默认6") Integer limit) {
        int lim = limit == null ? 6 : Math.max(1, Math.min(limit, 12));
        Long userId = AgentContext.current().getUserId();
        List<Product> list = recommendService.recommend(userId, productId, null, lim);
        AgentContext.current().addProducts(list);
        if (list == null || list.isEmpty()) {
            return searchProducts(null, null, lim);
        }
        return "推荐结果：\n" + summarizeProducts(list);
    }

    @Tool(description = "查看当前用户购物车内容。需要登录。")
    public String viewCart() {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录后再查看购物车。";
        }
        List<CartItem> items = cartService.list(userId);
        AgentContext.current().setCartItems(items);
        if (items.isEmpty()) {
            return "购物车是空的。可以先搜索商品再加购。";
        }
        StringBuilder sb = new StringBuilder("购物车共 ").append(items.size()).append(" 项：\n");
        for (CartItem item : items) {
            Product p = item.getProduct();
            String name = p != null ? p.getName() : ("商品#" + item.getProductId());
            Double price = p != null ? p.getPrice() : null;
            sb.append("- 购物车项ID=").append(item.getId())
                    .append("，商品ID=").append(item.getProductId())
                    .append("，").append(name)
                    .append("，数量=").append(item.getQuantity())
                    .append("，单价=").append(price == null ? "-" : ("¥" + price))
                    .append("\n");
            AgentContext.current().addProduct(p);
        }
        return sb.toString().trim();
    }

    @Tool(description = "将指定商品加入购物车。需要登录。quantity 默认1。")
    public String addToCart(
            @ToolParam(description = "商品ID") Long productId,
            @ToolParam(description = "数量，默认1") Integer quantity) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录后再加入购物车。";
        }
        if (productId == null) {
            return "请提供商品ID。";
        }
        try {
            CartItem item = cartService.add(userId, productId, quantity);
            AgentContext.current().addProduct(item.getProduct());
            AgentContext.current().setCartItems(cartService.list(userId));
            AgentContext.current().addAction("cart", "查看购物车", "/user/cart");
            String name = item.getProduct() != null ? item.getProduct().getName() : ("商品#" + productId);
            return "已加入购物车：" + name + " x" + item.getQuantity()
                    + "（购物车项ID=" + item.getId() + "）。可继续选购，或查看购物车后下单。";
        } catch (BusinessException e) {
            return "加购失败：" + e.getMessage();
        }
    }

    @Tool(description = "列出当前用户收货地址。需要登录。下单前应先调用本工具确认 addressId。")
    public String listAddresses() {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        List<UserAddress> list = addressService.list(userId);
        if (list.isEmpty()) {
            AgentContext.current().addAction("address", "添加收货地址", "/user/addresses");
            return "暂无收货地址，请先在个人中心添加地址后再下单。";
        }
        StringBuilder sb = new StringBuilder("收货地址列表：\n");
        for (UserAddress a : list) {
            sb.append("- 地址ID=").append(a.getId())
                    .append(Objects.equals(a.getIsDefault(), 1) ? "【默认】" : "")
                    .append("，").append(a.getReceiver()).append(" ").append(a.getPhone())
                    .append("，").append(joinAddress(a))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "预览结算（不下单）。addressId/cartItemIds 可省略：省略地址则用默认地址，省略购物车项则结算全部购物车。需要登录。")
    public String previewCheckout(
            @ToolParam(description = "收货地址ID，可空=默认地址") Long addressId,
            @ToolParam(description = "购物车项ID列表，逗号分隔；可空=全部购物车") String cartItemIds) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        try {
            return buildCheckoutPreview(userId, addressId, cartItemIds);
        } catch (BusinessException e) {
            return "预览失败：" + e.getMessage();
        }
    }

    @Tool(description = "一键结算购物车：confirmed=false 仅预览；用户明确同意后 confirmed=true 真正下单。地址/购物车项可省略（默认地址+全部购物车）。需要登录。优先使用本工具完成下单。")
    public String checkoutCart(
            @ToolParam(description = "收货地址ID，可空=默认地址") Long addressId,
            @ToolParam(description = "购物车项ID，逗号分隔；可空=全部") String cartItemIds,
            @ToolParam(description = "false=预览；true=用户已确认后真正下单") Boolean confirmed) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        try {
            if (!Boolean.TRUE.equals(confirmed)) {
                return buildCheckoutPreview(userId, addressId, cartItemIds)
                        + "\n用户回复「确认下单」后，请再次调用 checkoutCart 且 confirmed=true。";
            }
            return doCreateOrder(userId, addressId, cartItemIds);
        } catch (BusinessException e) {
            return "结算失败：" + e.getMessage();
        }
    }

    @Tool(description = "创建订单。必须 confirmed=true。addressId/cartItemIds 可省略（默认地址+全部购物车）。需要登录。")
    public String createOrder(
            @ToolParam(description = "收货地址ID，可空=默认地址") Long addressId,
            @ToolParam(description = "购物车项ID列表，逗号分隔；可空=全部") String cartItemIds,
            @ToolParam(description = "用户是否已明确确认下单，必须为 true") Boolean confirmed) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        if (!Boolean.TRUE.equals(confirmed)) {
            try {
                return "尚未确认下单。先给你预览：\n" + buildCheckoutPreview(userId, addressId, cartItemIds)
                        + "\n用户同意后请以 confirmed=true 再调用 createOrder 或 checkoutCart。";
            } catch (BusinessException e) {
                return "安全拦截：请先预览结算。预览失败：" + e.getMessage();
            }
        }
        try {
            return doCreateOrder(userId, addressId, cartItemIds);
        } catch (BusinessException e) {
            return "下单失败：" + e.getMessage();
        }
    }

    @Tool(description = "模拟支付订单。必须 confirmed=true。orderId 可省略则支付最近一笔待支付订单。需要登录。")
    public String payOrder(
            @ToolParam(description = "订单ID，可空=最近待支付订单") Long orderId,
            @ToolParam(description = "用户是否已确认支付") Boolean confirmed) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        if (!Boolean.TRUE.equals(confirmed)) {
            return "安全拦截：支付前请向用户确认订单与金额，同意后再以 confirmed=true 调用。";
        }
        try {
            Long payId = orderId;
            if (payId == null) {
                List<ShopOrder> recent = orderService.recentOrders(userId, 10);
                ShopOrder unpaid = recent.stream()
                        .filter(o -> o.getStatus() != null && o.getStatus() == 0)
                        .findFirst()
                        .orElse(null);
                if (unpaid == null) {
                    return "没有待支付订单。";
                }
                payId = unpaid.getId();
            }
            ShopOrder order = orderService.pay(userId, payId);
            AgentContext.current().addOrder(order);
            return "支付成功：订单ID=" + order.getId() + "，单号=" + order.getOrderNo()
                    + "，金额=¥" + money(order.getTotalAmount()) + "，状态=已支付待发货。";
        } catch (BusinessException e) {
            return "支付失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询当前用户最近订单。需要登录。")
    public String listOrders(
            @ToolParam(description = "返回条数，默认5") Integer limit) {
        Long userId = requireLogin();
        if (userId == null) {
            return "用户未登录，请先登录。";
        }
        int lim = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        List<ShopOrder> orders = orderService.recentOrders(userId, lim);
        AgentContext.current().setOrders(orders);
        if (orders.isEmpty()) {
            return "暂无订单。";
        }
        StringBuilder sb = new StringBuilder("最近订单：\n");
        for (ShopOrder o : orders) {
            sb.append("- 订单ID=").append(o.getId())
                    .append("，单号=").append(o.getOrderNo())
                    .append("，金额=¥").append(o.getTotalAmount())
                    .append("，状态=").append(statusText(o.getStatus()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildCheckoutPreview(Long userId, Long addressId, String cartItemIds) {
        UserAddress address = resolveAddress(userId, addressId);
        List<CartItem> selected = resolveCartItems(userId, cartItemIds);
        double total = 0;
        StringBuilder lines = new StringBuilder();
        for (CartItem item : selected) {
            Product p = ensureProduct(item);
            double price = p.getPrice() == null ? 0 : p.getPrice();
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            double line = Math.round(price * qty * 100.0) / 100.0;
            total += line;
            lines.append("- 购物车项ID=").append(item.getId())
                    .append("，商品ID=").append(item.getProductId())
                    .append("，").append(p.getName())
                    .append("，单价=¥").append(money(price))
                    .append("，数量=").append(qty)
                    .append("，小计=¥").append(money(line))
                    .append("\n");
            AgentContext.current().addProduct(p);
        }
        total = Math.round(total * 100.0) / 100.0;
        AgentContext.current().setCartItems(selected);
        return "结算预览（尚未下单）：\n"
                + "地址ID=" + address.getId()
                + "\n收货人：" + address.getReceiver() + " " + address.getPhone()
                + "\n地址：" + joinAddress(address)
                + "\n商品：\n" + lines
                + "应付合计：¥" + money(total)
                + "\n请把金额原样告诉用户；用户确认后调用 checkoutCart(confirmed=true) 或 createOrder(confirmed=true)。";
    }

    private String doCreateOrder(Long userId, Long addressId, String cartItemIds) {
        UserAddress address = resolveAddress(userId, addressId);
        List<CartItem> selected = resolveCartItems(userId, cartItemIds);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setAddressId(address.getId());
        req.setCartItemIds(selected.stream().map(CartItem::getId).collect(Collectors.toList()));
        List<ShopOrder> orders = orderService.create(userId, req);
        AgentContext.current().setOrders(orders);
        StringBuilder sb = new StringBuilder("下单成功，共 ").append(orders.size()).append(" 笔订单：\n");
        for (ShopOrder o : orders) {
            sb.append("- 订单ID=").append(o.getId())
                    .append("，单号=").append(o.getOrderNo())
                    .append("，金额=¥").append(money(o.getTotalAmount()))
                    .append("，状态=待支付\n");
        }
        sb.append("可提示用户支付；用户确认后调用 payOrder(confirmed=true)。");
        return sb.toString().trim();
    }

    private UserAddress resolveAddress(Long userId, Long addressId) {
        if (addressId != null) {
            return addressService.getById(userId, addressId);
        }
        List<UserAddress> list = addressService.list(userId);
        if (list.isEmpty()) {
            AgentContext.current().addAction("address", "添加收货地址", "/user/addresses");
            throw new BusinessException("暂无收货地址，请先添加地址后再下单");
        }
        return list.stream()
                .filter(a -> Objects.equals(a.getIsDefault(), 1))
                .findFirst()
                .orElse(list.get(0));
    }

    private List<CartItem> resolveCartItems(Long userId, String cartItemIds) {
        List<CartItem> all = cartService.list(userId);
        if (all.isEmpty()) {
            throw new BusinessException("购物车是空的，请先加购商品");
        }
        List<Long> ids = parseIds(cartItemIds);
        if (ids.isEmpty()) {
            return all;
        }
        List<CartItem> selected = all.stream().filter(i -> ids.contains(i.getId())).collect(Collectors.toList());
        if (selected.isEmpty()) {
            throw new BusinessException("购物车项无效，请先查看购物车");
        }
        return selected;
    }

    private Product ensureProduct(CartItem item) {
        Product p = item.getProduct();
        if (p != null && p.getPrice() != null && StringUtils.hasText(p.getName())) {
            return p;
        }
        Product loaded = productMapper.selectById(item.getProductId());
        if (loaded == null) {
            throw new BusinessException("商品不存在: " + item.getProductId());
        }
        loaded = productService.enrichOne(loaded);
        item.setProduct(loaded);
        return loaded;
    }

    private static String money(Double value) {
        double v = value == null ? 0 : value;
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private Long requireLogin() {
        AgentContext ctx = AgentContext.current();
        if (!ctx.isLoggedIn()) {
            ctx.markNeedLogin();
            return null;
        }
        return ctx.getUserId();
    }

    private static boolean matchKeyword(Product p, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return true;
        }
        String blob = ((p.getName() == null ? "" : p.getName()) + " "
                + (p.getDescription() == null ? "" : p.getDescription()) + " "
                + (p.getCategoryName() == null ? "" : p.getCategoryName())).toLowerCase(Locale.ROOT);
        for (String part : tokens) {
            if (StringUtils.hasText(part) && blob.contains(part.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int score(Product p, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return p.getSales() == null ? 0 : p.getSales();
        }
        int s = 0;
        String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
        String desc = p.getDescription() == null ? "" : p.getDescription().toLowerCase(Locale.ROOT);
        String cat = p.getCategoryName() == null ? "" : p.getCategoryName().toLowerCase(Locale.ROOT);
        for (String part : tokens) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String t = part.toLowerCase(Locale.ROOT);
            if (name.contains(t)) {
                s += 10;
            }
            if (desc.contains(t)) {
                s += 3;
            }
            if (cat.contains(t)) {
                s += 5;
            }
        }
        return s;
    }

    private static List<Product> filterProducts(List<Product> onShelf, List<String> tokens, Double maxPrice, int lim) {
        return onShelf.stream()
                .filter(p -> maxPrice == null || (p.getPrice() != null && p.getPrice() <= maxPrice))
                .filter(p -> matchKeyword(p, tokens))
                .sorted(Comparator
                        .comparingInt((Product p) -> score(p, tokens)).reversed()
                        .thenComparing(p -> p.getSales() == null ? 0 : p.getSales(), Comparator.reverseOrder()))
                .limit(lim)
                .collect(Collectors.toList());
    }

    /**
     * 从用户原话或模型传入词中提取可检索 token，并展开同义词。
     */
    static List<String> expandSearchTokens(String raw) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String text = raw.trim();
        // 已知品类词优先命中
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : SEARCH_ALIASES.entrySet()) {
            if (text.contains(e.getKey()) || lower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                tokens.add(e.getKey());
                tokens.addAll(e.getValue());
            }
        }
        // 去掉口语停用词后再拆分
        String cleaned = text;
        for (String stop : STOP_WORDS) {
            cleaned = cleaned.replace(stop, " ");
        }
        cleaned = cleaned
                .replaceAll("\\d+(?:\\.\\d+)?", " ")
                .replaceAll("[，,。.!！？?；;：:、|/\\\\（）()【】\\[\\]~～-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (StringUtils.hasText(cleaned)) {
            for (String part : cleaned.split("\\s+")) {
                if (part.length() >= 2 && part.length() <= 16 && !STOP_WORDS.contains(part)) {
                    tokens.add(part);
                    List<String> alias = SEARCH_ALIASES.get(part);
                    if (alias != null) {
                        tokens.addAll(alias);
                    }
                }
            }
        }
        // 仍为空时，尝试从原句截取 2 字滑动窗口命中别名表
        if (tokens.isEmpty()) {
            for (int i = 0; i + 1 < text.length(); i++) {
                String bi = text.substring(i, i + 2);
                if (SEARCH_ALIASES.containsKey(bi)) {
                    tokens.add(bi);
                    tokens.addAll(SEARCH_ALIASES.get(bi));
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String summarizeProducts(List<Product> list) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Product p : list) {
            sb.append(i++).append(". 商品ID=").append(p.getId())
                    .append("，").append(p.getName())
                    .append("，¥").append(p.getPrice())
                    .append("，库存=").append(p.getStock() == null ? 0 : p.getStock())
                    .append(p.getCategoryName() != null ? ("，分类=" + p.getCategoryName()) : "")
                    .append("\n");
        }
        sb.append("可将商品ID用于 addToCart。前端会展示商品卡片，回复中不要长篇罗列价格清单。");
        return sb.toString().trim();
    }

    private static List<Long> parseIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，\\s]+"))
                .filter(StringUtils::hasText)
                .map(s -> {
                    try {
                        return Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String joinAddress(UserAddress a) {
        return String.join("",
                nullToEmpty(a.getProvince()),
                nullToEmpty(a.getCity()),
                nullToEmpty(a.getDistrict()),
                nullToEmpty(a.getDetail()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "状态" + status;
        };
    }
}
