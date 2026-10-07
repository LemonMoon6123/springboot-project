package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.entity.Category;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.Product;
import com.scaffold.entity.ShopOrder;
import com.scaffold.mapper.CategoryMapper;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.ShopOrderMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    @Autowired
    private ShopOrderMapper shopOrderMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MerchantMapper merchantMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    public Map<String, Object> adminStats() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> salesTrend = salesByDay(null, 30);
        Map<String, Object> stock = stockStats(null);

        data.put("salesLast30Days", salesTrend);
        data.put("salesTrend", salesTrend);
        data.put("dailySales", salesTrend);
        data.put("topProducts", topProducts(null, 8));
        data.put("stockStats", stock);
        data.put("stockOverview", stockByCategory(null));
        data.put("stockByCategory", stockByCategory(null));
        data.put("orderStatusStats", orderStatusStats(null));
        data.put("orderCount", shopOrderMapper.selectCount(null));
        data.put("orders", shopOrderMapper.selectCount(null));
        data.put("productCount", productMapper.selectCount(null));
        data.put("userCount", userMapper.selectCount(null));
        data.put("users", userMapper.selectCount(null));
        data.put("merchantCount", merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)));
        data.put("merchants", merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)));
        Double revenue = sumPaidAmount(null);
        data.put("totalRevenue", revenue);
        data.put("salesAmount", revenue);
        data.put("todaySales", todaySales(null));
        return data;
    }

    public Map<String, Object> merchantStats(Long merchantId) {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> salesTrend = salesByDay(merchantId, 30);
        Map<String, Object> stock = stockStats(merchantId);
        data.put("salesLast30Days", salesTrend);
        data.put("salesTrend", salesTrend);
        data.put("dailySales", salesTrend);
        data.put("topProducts", topProducts(merchantId, 8));
        data.put("stockStats", stock);
        data.put("stockOverview", stockByCategory(merchantId));
        data.put("stockByCategory", stockByCategory(merchantId));
        data.put("orderStatusStats", orderStatusStats(merchantId));
        data.put("orderCount", shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getMerchantId, merchantId)));
        data.put("orders", data.get("orderCount"));
        data.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, merchantId)
                .eq(Product::getStatus, 1)));
        data.put("onSaleCount", data.get("productCount"));
        data.put("pendingShipCount", shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getMerchantId, merchantId)
                .eq(ShopOrder::getStatus, 1)));
        data.put("waitShip", data.get("pendingShipCount"));
        data.put("lowStockCount", stock.get("lowStock"));
        data.put("outOfStockCount", stock.get("outOfStock"));
        Double revenue = sumPaidAmount(merchantId);
        data.put("totalRevenue", revenue);
        data.put("salesAmount", revenue);
        data.put("todaySales", todaySales(merchantId));
        return data;
    }

    private Double todaySales(Long merchantId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .ge(ShopOrder::getPayTime, today + " 00:00:00")
                .le(ShopOrder::getPayTime, today + " 23:59:59")
                .in(ShopOrder::getStatus, 1, 2, 3);
        if (merchantId != null) {
            wrapper.eq(ShopOrder::getMerchantId, merchantId);
        }
        List<ShopOrder> orders = shopOrderMapper.selectList(wrapper);
        double sum = 0;
        for (ShopOrder o : orders) {
            sum += o.getTotalAmount() == null ? 0 : o.getTotalAmount();
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    private List<Map<String, Object>> orderStatusStats(Long merchantId) {
        String[] labels = {"待付款", "待发货", "待收货", "已完成", "已取消"};
        List<Map<String, Object>> list = new ArrayList<>();
        for (int status = 0; status <= 4; status++) {
            LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                    .eq(ShopOrder::getStatus, status);
            if (merchantId != null) {
                wrapper.eq(ShopOrder::getMerchantId, merchantId);
            }
            long count = shopOrderMapper.selectCount(wrapper);
            Map<String, Object> row = new HashMap<>();
            row.put("status", status);
            row.put("name", labels[status]);
            row.put("label", labels[status]);
            row.put("value", count);
            row.put("count", count);
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> stockByCategory(Long merchantId) {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Category category : categories) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, category.getId());
            if (merchantId != null) {
                wrapper.eq(Product::getMerchantId, merchantId);
            }
            List<Product> products = productMapper.selectList(wrapper);
            int stock = 0;
            for (Product p : products) {
                stock += p.getStock() == null ? 0 : p.getStock();
            }
            if (products.isEmpty() && stock == 0) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("name", category.getName());
            row.put("categoryName", category.getName());
            row.put("label", category.getName());
            row.put("stock", stock);
            row.put("value", stock);
            row.put("count", products.size());
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> salesByDay(Long merchantId, int days) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Double> dayMap = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            dayMap.put(today.minusDays(i).format(fmt), 0.0);
        }
        String start = today.minusDays(days - 1L) + " 00:00:00";
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .ge(ShopOrder::getPayTime, start)
                .in(ShopOrder::getStatus, 1, 2, 3);
        if (merchantId != null) {
            wrapper.eq(ShopOrder::getMerchantId, merchantId);
        }
        List<ShopOrder> orders = shopOrderMapper.selectList(wrapper);
        for (ShopOrder order : orders) {
            if (order.getPayTime() == null) {
                continue;
            }
            String day = order.getPayTime().length() >= 10 ? order.getPayTime().substring(0, 10) : null;
            if (day != null && dayMap.containsKey(day)) {
                dayMap.put(day, dayMap.get(day) + (order.getTotalAmount() == null ? 0 : order.getTotalAmount()));
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        dayMap.forEach((k, v) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("date", k);
            row.put("amount", Math.round(v * 100.0) / 100.0);
            list.add(row);
        });
        return list;
    }

    private List<Map<String, Object>> topProducts(Long merchantId, int limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getSales)
                .last("LIMIT " + limit);
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        return productMapper.selectList(wrapper).stream().map(p -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("sales", p.getSales());
            row.put("stock", p.getStock());
            row.put("price", p.getPrice());
            row.put("cover", p.getCover());
            return row;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> stockStats(Long merchantId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        List<Product> products = productMapper.selectList(wrapper);
        int total = products.size();
        int low = 0;
        int out = 0;
        int normal = 0;
        for (Product p : products) {
            int stock = p.getStock() == null ? 0 : p.getStock();
            if (stock <= 0) {
                out++;
            } else if (stock <= 10) {
                low++;
            } else {
                normal++;
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("normal", normal);
        map.put("lowStock", low);
        map.put("outOfStock", out);
        return map;
    }

    private Double sumPaidAmount(Long merchantId) {
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .in(ShopOrder::getStatus, 1, 2, 3);
        if (merchantId != null) {
            wrapper.eq(ShopOrder::getMerchantId, merchantId);
        }
        List<ShopOrder> orders = shopOrderMapper.selectList(wrapper);
        double sum = 0;
        for (ShopOrder o : orders) {
            sum += o.getTotalAmount() == null ? 0 : o.getTotalAmount();
        }
        return Math.round(sum * 100.0) / 100.0;
    }
}
