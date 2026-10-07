package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.entity.BrowseHistory;
import com.scaffold.entity.OrderItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductFavorite;
import com.scaffold.entity.ShopOrder;
import com.scaffold.mapper.BrowseHistoryMapper;
import com.scaffold.mapper.OrderItemMapper;
import com.scaffold.mapper.ProductFavoriteMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.ShopOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协同过滤推荐：
 * - UserCF：找兴趣相似用户，推荐他们喜欢而我未接触过的商品
 * - ItemCF：找与当前商品共现的商品（订单/收藏/浏览）
 */
@Service
public class RecommendService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductFavoriteMapper productFavoriteMapper;
    @Autowired
    private ShopOrderMapper shopOrderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private BrowseHistoryMapper browseHistoryMapper;
    @Autowired
    private ProductService productService;

    public List<Product> recommend(Long userId, Long productId, Long categoryId, int limit) {
        return recommend(userId, productId, categoryId, null, limit);
    }

    public List<Product> recommend(Long userId, Long productId, Long categoryId, String mode, int limit) {
        int lim = Math.max(1, Math.min(limit, 30));
        String m = StringUtils.hasText(mode) ? mode.trim().toLowerCase() : "auto";
        LinkedUnique products = new LinkedUnique();

        if ("usercf".equals(m)) {
            if (userId != null) {
                userCf(userId, products, lim);
            }
        } else if ("itemcf".equals(m)) {
            if (productId != null) {
                itemCf(productId, products, lim);
            } else if (userId != null) {
                // 无种子商品时，用用户最近行为商品做 ItemCF
                for (Long seed : recentSeeds(userId, 3)) {
                    itemCf(seed, products, lim);
                    if (products.size() >= lim) {
                        break;
                    }
                }
            }
        } else {
            // auto: 详情页优先 ItemCF，首页优先 UserCF
            if (productId != null) {
                itemCf(productId, products, lim);
                if (userId != null && products.size() < lim) {
                    userCf(userId, products, lim);
                }
            } else if (userId != null) {
                userCf(userId, products, lim);
            }
        }

        if (productId != null && products.size() < lim) {
            Product seed = productMapper.selectById(productId);
            if (seed != null && seed.getCategoryId() != null) {
                addCategory(seed.getCategoryId(), productId, products, lim);
            }
        }
        if (categoryId != null && products.size() < lim) {
            addCategory(categoryId, productId, products, lim);
        }
        if (products.size() < lim) {
            for (Product p : productService.listHot(lim * 2)) {
                if (productId != null && productId.equals(p.getId())) {
                    continue;
                }
                products.add(p);
                if (products.size() >= lim) {
                    break;
                }
            }
        }
        List<Product> result = products.list(lim);
        productService.enrich(result);
        return result;
    }

    /** UserCF：基于用户-物品交互矩阵的相似用户推荐 */
    private void userCf(Long userId, LinkedUnique products, int lim) {
        Set<Long> myItems = userItems(userId);
        if (myItems.isEmpty()) {
            return;
        }
        Map<Long, Set<Long>> userItemMap = loadUserItemMap(200);
        userItemMap.remove(userId);

        Map<Long, Double> similarUsers = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : userItemMap.entrySet()) {
            double sim = jaccard(myItems, e.getValue());
            if (sim > 0) {
                similarUsers.put(e.getKey(), sim);
            }
        }
        Map<Long, Double> score = new HashMap<>();
        similarUsers.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(30)
                .forEach(e -> {
                    Set<Long> their = userItemMap.getOrDefault(e.getKey(), Set.of());
                    for (Long pid : their) {
                        if (!myItems.contains(pid)) {
                            score.merge(pid, e.getValue(), Double::sum);
                        }
                    }
                });
        addByScore(score, products, lim, null);
    }

    /** ItemCF：基于物品共现的相似商品推荐 */
    private void itemCf(Long productId, LinkedUnique products, int lim) {
        if (productId == null) {
            return;
        }
        // 找到与种子商品同现于同一订单/同用户收藏或浏览的其他商品
        Map<Long, Double> score = new HashMap<>();

        // 订单共现
        List<OrderItem> seedOrders = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getProductId, productId)
                .last("LIMIT 80"));
        Set<Long> orderIds = seedOrders.stream().map(OrderItem::getOrderId).collect(Collectors.toSet());
        if (!orderIds.isEmpty()) {
            List<OrderItem> coItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .in(OrderItem::getOrderId, orderIds)
                    .ne(OrderItem::getProductId, productId));
            for (OrderItem oi : coItems) {
                score.merge(oi.getProductId(), 2.0, Double::sum);
            }
        }

        // 收藏共现用户
        List<ProductFavorite> seedFavs = productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getProductId, productId)
                .last("LIMIT 80"));
        for (ProductFavorite fav : seedFavs) {
            List<ProductFavorite> others = productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                    .eq(ProductFavorite::getUserId, fav.getUserId())
                    .ne(ProductFavorite::getProductId, productId)
                    .last("LIMIT 40"));
            for (ProductFavorite o : others) {
                score.merge(o.getProductId(), 1.5, Double::sum);
            }
        }

        // 浏览共现用户
        List<BrowseHistory> seedViews = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getProductId, productId)
                .last("LIMIT 80"));
        for (BrowseHistory view : seedViews) {
            List<BrowseHistory> others = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUserId, view.getUserId())
                    .ne(BrowseHistory::getProductId, productId)
                    .last("LIMIT 40"));
            for (BrowseHistory o : others) {
                score.merge(o.getProductId(), 1.0, Double::sum);
            }
        }

        addByScore(score, products, lim, productId);
    }

    private Set<Long> userItems(Long userId) {
        Set<Long> ids = new HashSet<>();
        productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                        .eq(ProductFavorite::getUserId, userId))
                .forEach(f -> ids.add(f.getProductId()));
        browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .last("LIMIT 100"))
                .forEach(h -> ids.add(h.getProductId()));
        List<ShopOrder> orders = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .in(ShopOrder::getStatus, 1, 2, 3)
                .last("LIMIT 50"));
        for (ShopOrder order : orders) {
            orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getOrderId, order.getId()))
                    .forEach(i -> ids.add(i.getProductId()));
        }
        return ids;
    }

    private List<Long> recentSeeds(Long userId, int limit) {
        List<Long> seeds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BrowseHistory h : browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getViewedAt)
                .last("LIMIT " + limit * 2))) {
            if (seen.add(h.getProductId())) {
                seeds.add(h.getProductId());
            }
            if (seeds.size() >= limit) {
                return seeds;
            }
        }
        for (ProductFavorite f : productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .orderByDesc(ProductFavorite::getId)
                .last("LIMIT " + limit))) {
            if (seen.add(f.getProductId())) {
                seeds.add(f.getProductId());
            }
            if (seeds.size() >= limit) {
                break;
            }
        }
        return seeds;
    }

    private Map<Long, Set<Long>> loadUserItemMap(int userLimit) {
        Map<Long, Set<Long>> map = new HashMap<>();
        List<ProductFavorite> favs = productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                .orderByDesc(ProductFavorite::getId)
                .last("LIMIT 800"));
        for (ProductFavorite f : favs) {
            map.computeIfAbsent(f.getUserId(), k -> new HashSet<>()).add(f.getProductId());
        }
        List<BrowseHistory> views = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .orderByDesc(BrowseHistory::getId)
                .last("LIMIT 800"));
        for (BrowseHistory h : views) {
            map.computeIfAbsent(h.getUserId(), k -> new HashSet<>()).add(h.getProductId());
        }
        List<ShopOrder> orders = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .in(ShopOrder::getStatus, 1, 2, 3)
                .orderByDesc(ShopOrder::getId)
                .last("LIMIT " + userLimit));
        for (ShopOrder order : orders) {
            List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId()));
            for (OrderItem item : items) {
                map.computeIfAbsent(order.getUserId(), k -> new HashSet<>()).add(item.getProductId());
            }
        }
        return map;
    }

    private double jaccard(Set<Long> a, Set<Long> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (Long id : a) {
            if (b.contains(id)) {
                inter++;
            }
        }
        if (inter == 0) {
            return 0;
        }
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private void addByScore(Map<Long, Double> score, LinkedUnique products, int lim, Long excludeId) {
        score.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(lim * 2L)
                .forEach(e -> {
                    if (excludeId != null && excludeId.equals(e.getKey())) {
                        return;
                    }
                    Product p = productMapper.selectById(e.getKey());
                    if (p != null && p.getStatus() != null && p.getStatus() == 1) {
                        products.add(p);
                    }
                });
    }

    private void addCategory(Long categoryId, Long excludeId, LinkedUnique products, int lim) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, categoryId)
                .eq(Product::getStatus, 1)
                .ne(excludeId != null, Product::getId, excludeId)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + lim));
        list.forEach(products::add);
    }

    private static class LinkedUnique {
        private final List<Product> list = new ArrayList<>();
        private final Set<Long> ids = new HashSet<>();

        void add(Product p) {
            if (p == null || p.getId() == null || ids.contains(p.getId())) {
                return;
            }
            if (p.getStatus() != null && p.getStatus() != 1) {
                return;
            }
            ids.add(p.getId());
            list.add(p);
        }

        int size() {
            return list.size();
        }

        List<Product> list(int lim) {
            return list.stream().limit(lim).collect(Collectors.toList());
        }
    }
}
