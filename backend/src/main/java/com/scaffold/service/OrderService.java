package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.BusinessException;
import com.scaffold.common.DateTimes;
import com.scaffold.common.PageResult;
import com.scaffold.dto.AfterSaleApplyRequest;
import com.scaffold.dto.AfterSaleHandleRequest;
import com.scaffold.dto.CreateOrderRequest;
import com.scaffold.dto.LogisticsNode;
import com.scaffold.dto.ShipOrderRequest;
import com.scaffold.entity.CartItem;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.OrderItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductReview;
import com.scaffold.entity.ShopOrder;
import com.scaffold.entity.User;
import com.scaffold.entity.UserAddress;
import com.scaffold.mapper.CartItemMapper;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.OrderItemMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.ProductReviewMapper;
import com.scaffold.mapper.ShopOrderMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class OrderService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ShopOrderMapper shopOrderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MerchantMapper merchantMapper;
    @Autowired
    private ProductReviewMapper productReviewMapper;
    @Autowired
    private AddressService addressService;
    @Autowired
    private ProductService productService;

    @Transactional
    public List<ShopOrder> create(Long userId, CreateOrderRequest request) {
        if (request.getAddressId() == null) {
            throw new BusinessException("请选择收货地址");
        }
        if (request.getCartItemIds() == null || request.getCartItemIds().isEmpty()) {
            throw new BusinessException("请选择购物车商品");
        }
        UserAddress address = addressService.getById(userId, request.getAddressId());
        String addressSnapshot = toAddressSnapshot(address);

        List<CartItem> cartItems = cartItemMapper.selectBatchIds(request.getCartItemIds());
        if (cartItems.size() != request.getCartItemIds().size()) {
            throw new BusinessException("购物车商品无效");
        }
        for (CartItem item : cartItems) {
            if (!item.getUserId().equals(userId)) {
                throw new BusinessException("购物车商品无效");
            }
        }

        Map<Long, List<CartItem>> byMerchant = new LinkedHashMap<>();
        for (CartItem cartItem : cartItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            if (product == null) {
                throw new BusinessException("商品不存在: " + cartItem.getProductId());
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException("商品已下架: " + product.getName());
            }
            int stock = product.getStock() == null ? 0 : product.getStock();
            if (stock < cartItem.getQuantity()) {
                throw new BusinessException("库存不足: " + product.getName());
            }
            cartItem.setProduct(product);
            byMerchant.computeIfAbsent(product.getMerchantId(), k -> new ArrayList<>()).add(cartItem);
        }

        List<ShopOrder> created = new ArrayList<>();
        for (Map.Entry<Long, List<CartItem>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            List<CartItem> items = entry.getValue();
            double total = 0;
            for (CartItem ci : items) {
                total += ci.getProduct().getPrice() * ci.getQuantity();
            }
            total = Math.round(total * 100.0) / 100.0;

            ShopOrder order = new ShopOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setMerchantId(merchantId);
            order.setAddressSnapshot(addressSnapshot);
            order.setTotalAmount(total);
            order.setStatus(0);
            shopOrderMapper.insert(order);

            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem ci : items) {
                Product p = ci.getProduct();
                OrderItem oi = new OrderItem();
                oi.setOrderId(order.getId());
                oi.setProductId(p.getId());
                oi.setProductName(p.getName());
                oi.setProductCover(p.getCover());
                oi.setPrice(p.getPrice());
                oi.setQuantity(ci.getQuantity());
                orderItemMapper.insert(oi);
                orderItems.add(oi);
                productService.changeStock(p.getId(), -ci.getQuantity());
            }
            order.setItems(orderItems);
            created.add(order);
        }

        cartItemMapper.deleteBatchIds(request.getCartItemIds());
        return created;
    }

    public PageResult<ShopOrder> pageForUser(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(ShopOrder::getStatus, status);
        }
        wrapper.orderByDesc(ShopOrder::getId);
        Page<ShopOrder> result = shopOrderMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillItems);
        return PageResult.of(result);
    }

    public PageResult<ShopOrder> pageForMerchant(Long merchantId, Integer status, int page, int size) {
        return pageForMerchant(merchantId, status, null, page, size);
    }

    public PageResult<ShopOrder> pageForMerchant(Long merchantId, Integer status, Integer afterSaleStatus,
                                                 int page, int size) {
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getMerchantId, merchantId);
        if (status != null) {
            wrapper.eq(ShopOrder::getStatus, status);
        }
        if (afterSaleStatus != null) {
            wrapper.eq(ShopOrder::getAfterSaleStatus, afterSaleStatus);
        }
        wrapper.orderByDesc(ShopOrder::getId);
        Page<ShopOrder> result = shopOrderMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillItems);
        return PageResult.of(result);
    }

    public PageResult<ShopOrder> pageForAdmin(Long userId, Long merchantId, Integer status,
                                              String keyword, int page, int size) {
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ShopOrder::getUserId, userId);
        }
        if (merchantId != null) {
            wrapper.eq(ShopOrder::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(ShopOrder::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ShopOrder::getOrderNo, keyword.trim());
        }
        wrapper.orderByDesc(ShopOrder::getId);
        Page<ShopOrder> result = shopOrderMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillItems);
        return PageResult.of(result);
    }

    public ShopOrder getDetail(Long id) {
        ShopOrder order = shopOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        fillItems(order);
        return order;
    }

    public ShopOrder getOwnedByUser(Long userId, Long id) {
        ShopOrder order = getDetail(id);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看该订单");
        }
        return order;
    }

    @Transactional
    public ShopOrder pay(Long userId, Long id) {
        ShopOrder order = getOwnedByUser(userId, id);
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException("订单状态不可支付");
        }
        User user = userMapper.selectById(userId);
        double balance = user.getBalance() == null ? 0.0 : user.getBalance();
        double amount = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
        if (balance < amount) {
            throw new BusinessException("余额不足，请先充值");
        }
        User patch = new User();
        patch.setId(userId);
        patch.setBalance(Math.round((balance - amount) * 100.0) / 100.0);
        userMapper.updateById(patch);

        ShopOrder orderPatch = new ShopOrder();
        orderPatch.setId(id);
        orderPatch.setStatus(1);
        orderPatch.setPayTime(DateTimes.now());
        shopOrderMapper.updateById(orderPatch);

        for (OrderItem item : order.getItems()) {
            productService.increaseSales(item.getProductId(), item.getQuantity());
        }
        return getDetail(id);
    }

    @Transactional
    public ShopOrder cancel(Long userId, Long id) {
        ShopOrder order = getOwnedByUser(userId, id);
        Integer status = order.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("仅待支付或待发货订单可取消");
        }
        if (afterSalePending(order)) {
            throw new BusinessException("售后处理中，暂不可取消");
        }
        if (status == 1) {
            refundToUser(order);
            restoreStockAndSales(order);
        } else {
            for (OrderItem item : order.getItems()) {
                productService.changeStock(item.getProductId(), item.getQuantity());
            }
        }
        ShopOrder patch = new ShopOrder();
        patch.setId(id);
        patch.setStatus(4);
        shopOrderMapper.updateById(patch);
        return getDetail(id);
    }

    @Transactional
    public ShopOrder applyAfterSale(Long userId, Long id, AfterSaleApplyRequest request) {
        if (request == null || request.getType() == null) {
            throw new BusinessException("请选择售后类型");
        }
        int type = request.getType();
        if (type < 1 || type > 3) {
            throw new BusinessException("售后类型无效");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写售后原因");
        }
        if (reason.length() > 500) {
            throw new BusinessException("售后原因过长");
        }
        ShopOrder order = getOwnedByUser(userId, id);
        Integer status = order.getStatus();
        if (status == null || (status != 2 && status != 3)) {
            throw new BusinessException("仅已发货或已完成订单可申请售后");
        }
        Integer as = order.getAfterSaleStatus();
        if (as != null && as == 1) {
            throw new BusinessException("售后申请处理中，请勿重复提交");
        }
        if (as != null && as == 2) {
            throw new BusinessException("该订单售后已完成");
        }
        ShopOrder patch = new ShopOrder();
        patch.setId(id);
        patch.setAfterSaleStatus(1);
        patch.setAfterSaleType(type);
        patch.setAfterSaleReason(reason);
        patch.setAfterSaleReply(null);
        patch.setAfterSaleAt(DateTimes.now());
        shopOrderMapper.updateById(patch);
        return getDetail(id);
    }

    @Transactional
    public ShopOrder handleAfterSale(Long merchantId, Long id, AfterSaleHandleRequest request, boolean admin) {
        if (request == null || request.getApprove() == null) {
            throw new BusinessException("请选择同意或拒绝");
        }
        ShopOrder order = getDetail(id);
        if (!admin && !order.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权处理该售后");
        }
        if (order.getAfterSaleStatus() == null || order.getAfterSaleStatus() != 1) {
            throw new BusinessException("当前无待处理的售后申请");
        }
        String reply = request.getReply() == null ? "" : request.getReply().trim();
        if (!Boolean.TRUE.equals(request.getApprove()) && !StringUtils.hasText(reply)) {
            throw new BusinessException("拒绝时请填写原因");
        }
        if (reply.length() > 500) {
            throw new BusinessException("回复内容过长");
        }

        ShopOrder patch = new ShopOrder();
        patch.setId(id);
        patch.setAfterSaleAt(DateTimes.now());
        if (Boolean.TRUE.equals(request.getApprove())) {
            patch.setAfterSaleStatus(2);
            patch.setAfterSaleReply(StringUtils.hasText(reply) ? reply : "商家已同意售后");
            Integer type = order.getAfterSaleType();
            // 仅退款 / 退货退款：退回余额、恢复库存与销量，订单关闭
            if (type != null && (type == 1 || type == 2)) {
                if (order.getStatus() != null && order.getStatus() != 4) {
                    refundToUser(order);
                    restoreStockAndSales(order);
                    patch.setStatus(4);
                }
            }
            // 换货：仅标记同意，线下协商换货，不自动退款
        } else {
            patch.setAfterSaleStatus(3);
            patch.setAfterSaleReply(reply);
        }
        shopOrderMapper.updateById(patch);
        return getDetail(id);
    }

    private void refundToUser(ShopOrder order) {
        if (order.getUserId() == null) {
            return;
        }
        double amount = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
        if (amount <= 0) {
            return;
        }
        User user = userMapper.selectById(order.getUserId());
        if (user == null) {
            return;
        }
        double balance = user.getBalance() == null ? 0.0 : user.getBalance();
        User patch = new User();
        patch.setId(user.getId());
        patch.setBalance(Math.round((balance + amount) * 100.0) / 100.0);
        userMapper.updateById(patch);
    }

    private void restoreStockAndSales(ShopOrder order) {
        if (order.getItems() == null) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            productService.changeStock(item.getProductId(), item.getQuantity());
            productService.decreaseSales(item.getProductId(), item.getQuantity());
        }
    }

    private boolean afterSalePending(ShopOrder order) {
        return order.getAfterSaleStatus() != null && order.getAfterSaleStatus() == 1;
    }

    private boolean canApplyAfterSale(ShopOrder order) {
        Integer status = order.getStatus();
        if (status == null || (status != 2 && status != 3)) {
            return false;
        }
        Integer as = order.getAfterSaleStatus();
        return as == null || as == 0 || as == 3;
    }

    public ShopOrder ship(Long id, Long operatorId, boolean admin) {
        return ship(id, operatorId, admin, null);
    }

    public ShopOrder ship(Long id, Long operatorId, boolean admin, ShipOrderRequest request) {
        ShopOrder order = getDetail(id);
        if (!admin && !order.getMerchantId().equals(operatorId)) {
            throw new BusinessException(403, "无权发货该订单");
        }
        if (order.getStatus() == null || order.getStatus() != 1) {
            throw new BusinessException("仅待发货订单可发货");
        }
        String company = request != null && StringUtils.hasText(request.getExpressCompany())
                ? request.getExpressCompany().trim()
                : "商家配送";
        String trackingNo = request != null && StringUtils.hasText(request.getTrackingNo())
                ? request.getTrackingNo().trim()
                : ("AUTO" + System.currentTimeMillis() % 100000000L);
        String remark = request != null ? request.getRemark() : null;
        String shipTime = DateTimes.now();

        ShopOrder patch = new ShopOrder();
        patch.setId(id);
        patch.setStatus(2);
        patch.setShipTime(shipTime);
        patch.setExpressCompany(company);
        patch.setTrackingNo(trackingNo);
        patch.setLogisticsJson(buildShippedLogisticsJson(order, company, trackingNo, shipTime, remark));
        shopOrderMapper.updateById(patch);
        return getDetail(id);
    }

    public ShopOrder confirm(Long userId, Long id) {
        ShopOrder order = getOwnedByUser(userId, id);
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new BusinessException("仅已发货订单可确认收货");
        }
        String finishTime = DateTimes.now();
        ShopOrder patch = new ShopOrder();
        patch.setId(id);
        patch.setStatus(3);
        patch.setFinishTime(finishTime);
        patch.setLogisticsJson(appendSignedNode(order.getLogisticsJson(), finishTime));
        shopOrderMapper.updateById(patch);
        return getDetail(id);
    }

    public List<ShopOrder> recentOrders(Long userId, int limit) {
        List<ShopOrder> list = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .orderByDesc(ShopOrder::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        list.forEach(this::fillItems);
        return list;
    }

    public Map<String, Long> statsForUser(Long userId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("unpaid", countByStatus(userId, 0));
        stats.put("unshipped", countByStatus(userId, 1));
        stats.put("unreceived", countByStatus(userId, 2));
        stats.put("completed", countByStatus(userId, 3));
        stats.put("cancelled", countByStatus(userId, 4));
        Long afterSale = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .eq(ShopOrder::getAfterSaleStatus, 1));
        stats.put("afterSale", afterSale == null ? 0L : afterSale);
        Long total = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId));
        stats.put("total", total == null ? 0L : total);
        return stats;
    }

    private long countByStatus(Long userId, int status) {
        Long c = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .eq(ShopOrder::getStatus, status));
        return c == null ? 0L : c;
    }

    private void fillItems(ShopOrder order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        fillReviewFlags(order, items);
        order.setItems(items);
        if (order.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(order.getMerchantId());
            if (merchant != null) {
                if (merchant.getShopName() != null && !merchant.getShopName().isBlank()) {
                    order.setShopName(merchant.getShopName());
                } else if (merchant.getNickname() != null && !merchant.getNickname().isBlank()) {
                    order.setShopName(merchant.getNickname());
                } else {
                    order.setShopName(merchant.getUsername());
                }
                order.setShopAvatar(merchant.getAvatar());
            }
        }
        if (order.getUserId() != null) {
            User buyer = userMapper.selectById(order.getUserId());
            if (buyer != null) {
                order.setBuyerUsername(buyer.getUsername());
                order.setBuyerNickname(buyer.getNickname());
                order.setBuyerPhone(buyer.getPhone());
                order.setBuyerAvatar(buyer.getAvatar());
            }
        }
        order.setLogisticsTrace(parseLogistics(order));
        order.setCanApplyAfterSale(canApplyAfterSale(order));
    }

    private void fillReviewFlags(ShopOrder order, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            order.setReviewed(Boolean.TRUE);
            order.setCanReview(Boolean.FALSE);
            return;
        }
        List<Long> itemIds = items.stream().map(OrderItem::getId).filter(id -> id != null).toList();
        java.util.Set<Long> reviewedIds = new java.util.HashSet<>();
        if (!itemIds.isEmpty()) {
            List<ProductReview> reviews = productReviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                    .in(ProductReview::getOrderItemId, itemIds)
                    .select(ProductReview::getOrderItemId));
            for (ProductReview review : reviews) {
                if (review.getOrderItemId() != null) {
                    reviewedIds.add(review.getOrderItemId());
                }
            }
        }
        int reviewedCount = 0;
        for (OrderItem item : items) {
            boolean done = item.getId() != null && reviewedIds.contains(item.getId());
            item.setReviewed(done);
            if (done) {
                reviewedCount++;
            }
        }
        boolean allReviewed = reviewedCount >= items.size();
        order.setReviewed(allReviewed);
        order.setCanReview(order.getStatus() != null && order.getStatus() == 3 && !allReviewed);
    }

    private List<LogisticsNode> parseLogistics(ShopOrder order) {
        if (StringUtils.hasText(order.getLogisticsJson())) {
            try {
                List<LogisticsNode> nodes = OBJECT_MAPPER.readValue(
                        order.getLogisticsJson(),
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, LogisticsNode.class)
                );
                if (nodes != null && !nodes.isEmpty()) {
                    return nodes;
                }
            } catch (Exception ignored) {
                // fallback below
            }
        }
        return buildFallbackLogistics(order);
    }

    private List<LogisticsNode> buildFallbackLogistics(ShopOrder order) {
        List<LogisticsNode> nodes = new ArrayList<>();
        nodes.add(node("created", "订单已创建", "买家提交订单，等待付款",
                order.getCreatedAt(), true, false));
        if (order.getPayTime() != null || (order.getStatus() != null && order.getStatus() >= 1)) {
            nodes.add(node("paid", "买家已付款", "商家备货中",
                    order.getPayTime() != null ? order.getPayTime() : order.getCreatedAt(), true, false));
        }
        if (order.getShipTime() != null || (order.getStatus() != null && order.getStatus() >= 2)) {
            String company = StringUtils.hasText(order.getExpressCompany()) ? order.getExpressCompany() : "快递";
            String tracking = StringUtils.hasText(order.getTrackingNo()) ? order.getTrackingNo() : "—";
            nodes.add(node("shipped", "商家已发货", company + " " + tracking,
                    order.getShipTime(), true, order.getStatus() != null && order.getStatus() == 2));
        }
        if (order.getFinishTime() != null || (order.getStatus() != null && order.getStatus() >= 3 && order.getStatus() != 4)) {
            nodes.add(node("signed", "已签收", "买家确认收货，交易完成",
                    order.getFinishTime(), true, order.getStatus() != null && order.getStatus() == 3));
        }
        markLatestCurrent(nodes);
        Collections.reverse(nodes);
        return nodes;
    }

    private String buildShippedLogisticsJson(ShopOrder order, String company, String trackingNo,
                                             String shipTime, String remark) {
        List<LogisticsNode> nodes = new ArrayList<>();
        nodes.add(node("created", "订单已创建", "买家提交订单",
                safeTime(order.getCreatedAt(), shipTime), true, false));
        nodes.add(node("paid", "买家已付款", "商家已确认收款并开始备货",
                safeTime(order.getPayTime(), shipTime), true, false));
        String shipDesc = company + " · 运单号 " + trackingNo;
        if (StringUtils.hasText(remark)) {
            shipDesc = shipDesc + "（" + remark.trim() + "）";
        }
        nodes.add(node("shipped", "商家已发货", shipDesc, shipTime, true, false));
        nodes.add(node("collected", "揽收成功", company + "已取件，包裹进入转运中心",
                DateTimes.plusHours(shipTime, 2), true, false));
        nodes.add(node("transit", "运输中", "包裹正在运往收货城市",
                DateTimes.plusHours(shipTime, 18), true, false));
        nodes.add(node("delivering", "派送中", "快递员正在派送，请保持电话畅通",
                DateTimes.plusHours(shipTime, 42), true, true));
        Collections.reverse(nodes);
        try {
            return OBJECT_MAPPER.writeValueAsString(nodes);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String appendSignedNode(String logisticsJson, String finishTime) {
        List<LogisticsNode> nodes = new ArrayList<>();
        if (StringUtils.hasText(logisticsJson)) {
            try {
                List<LogisticsNode> existing = OBJECT_MAPPER.readValue(
                        logisticsJson,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, LogisticsNode.class)
                );
                if (existing != null) {
                    nodes.addAll(existing);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        // stored newest-first; normalize to oldest-first for rebuild
        Collections.reverse(nodes);
        for (LogisticsNode n : nodes) {
            n.setCurrent(false);
            n.setDone(true);
        }
        nodes.add(node("signed", "已签收", "买家确认收货，交易完成", finishTime, true, true));
        Collections.reverse(nodes);
        try {
            return OBJECT_MAPPER.writeValueAsString(nodes);
        } catch (Exception ex) {
            return logisticsJson;
        }
    }

    private void markLatestCurrent(List<LogisticsNode> oldestFirst) {
        for (LogisticsNode n : oldestFirst) {
            n.setCurrent(false);
        }
        for (int i = oldestFirst.size() - 1; i >= 0; i--) {
            LogisticsNode n = oldestFirst.get(i);
            if (Boolean.TRUE.equals(n.getDone())) {
                n.setCurrent(true);
                break;
            }
        }
    }

    private LogisticsNode node(String code, String title, String desc, String time,
                               boolean done, boolean current) {
        LogisticsNode node = new LogisticsNode();
        node.setCode(code);
        node.setTitle(title);
        node.setDesc(desc);
        node.setTime(time);
        node.setDone(done);
        node.setCurrent(current);
        return node;
    }

    private String safeTime(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String generateOrderNo() {
        return System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }

    private String toAddressSnapshot(UserAddress address) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("receiver", address.getReceiver());
            map.put("phone", address.getPhone());
            map.put("province", address.getProvince());
            map.put("city", address.getCity());
            map.put("district", address.getDistrict());
            map.put("detail", address.getDetail());
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return address.getReceiver() + " " + address.getPhone() + " "
                    + address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
        }
    }
}
