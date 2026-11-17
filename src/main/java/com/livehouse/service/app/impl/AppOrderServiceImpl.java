package com.livehouse.service.app.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.livehouse.config.LivehouseProperties;
import com.livehouse.dto.OrderTimeoutMessage;
import com.livehouse.dto.app.OrderCreateRequest;
import com.livehouse.dto.app.OrderPreviewRequest;
import com.livehouse.entity.*;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.*;
import com.livehouse.service.IMessageProducerService;
import com.livehouse.service.IShowService;
import com.livehouse.service.ITicketOrderService;
import com.livehouse.service.ITicketTypeService;
import com.livehouse.service.app.AppOrderService;
import com.livehouse.service.app.PriceCalculator;
import com.livehouse.utils.RedisConstants;
import com.livehouse.utils.RedisIDGenerator;
import com.livehouse.utils.SeckillScriptExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppOrderServiceImpl implements AppOrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ITicketTypeService ticketTypeService;
    @Resource
    private IShowService showService;
    @Resource
    private VenueMapper venueMapper;
    @Resource
    private PerformanceMapper performanceMapper;
    @Resource
    private ITicketOrderService ticketOrderService;
    @Resource
    private AudienceMapper audienceMapper;
    @Resource
    private OrderAudienceMapper orderAudienceMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private MemberLevelMapper memberLevelMapper;
    @Resource
    private PriceCalculator priceCalculator;
    @Resource
    private LivehouseProperties livehouseProperties;
    @Resource
    private RedisIDGenerator redisIDGenerator;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SeckillScriptExecutor seckillScriptExecutor;
    @Resource
    private IMessageProducerService messageProducerService;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private RefundRecordMapper refundRecordMapper;

    @Override
    public Map<String, Object> preview(Long userId, OrderPreviewRequest request) {
        PreviewContext ctx = validateAndBuild(userId, request.getShowId(), request.getTicketTypeId(),
                request.getQuantity(), request.getAudienceIds(), false);
        PriceCalculator.PriceBreakdown price = ctx.price;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("showId", String.valueOf(request.getShowId()));
        data.put("title", ctx.title);
        data.put("showTimeText", ctx.showTimeText);
        data.put("venueName", ctx.venueName);
        data.put("ticketTypeName", ctx.ticketType.getName());
        data.put("unitPrice", ctx.ticketType.getPrice());
        data.put("quantity", request.getQuantity());
        data.put("totalAmount", price.getTotalAmount());
        data.put("serviceFee", price.getServiceFee());
        data.put("memberLevel", price.getMemberLevel());
        data.put("memberLevelName", price.getMemberLevelName());
        data.put("memberDiscountRate", price.getMemberDiscountRate());
        data.put("memberDiscountAmount", price.getMemberDiscountAmount());
        data.put("payAmount", price.getPayAmount());
        data.put("audiences", ctx.audienceBriefs);
        data.put("remainSeconds", livehouseProperties.getOrder().getPayTimeoutMinutes() * 60L);
        data.put("canBuy", ctx.blockReason == null);
        data.put("blockReason", ctx.blockReason);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> create(Long userId, OrderCreateRequest request) {
        if (StrUtil.isBlank(request.getIdempotentKey())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "idempotentKey 不能为空");
        }

        TicketOrder existing = ticketOrderService.lambdaQuery()
                .eq(TicketOrder::getIdempotentKey, request.getIdempotentKey())
                .one();
        if (existing != null) {
            return buildCreateResponse(existing);
        }

        PreviewContext ctx = validateAndBuild(userId, request.getShowId(), request.getTicketTypeId(),
                request.getQuantity(), request.getAudienceIds(), true);
        if (ctx.blockReason != null) {
            throw new StatusCodeException(StatusCode.STOCK_NOT_ENOUGH, ctx.blockReason);
        }

        String userOrderStatusKey = "user:order:status:" + userId + ":" + request.getTicketTypeId();
        if ("PENDING".equals(stringRedisTemplate.opsForValue().get(userOrderStatusKey))) {
            TicketOrder pending = ticketOrderService.lambdaQuery()
                    .eq(TicketOrder::getUserId, userId)
                    .eq(TicketOrder::getTicketTypeId, request.getTicketTypeId())
                    .eq(TicketOrder::getOrderStatus, 1)
                    .eq(TicketOrder::getPayStatus, 0)
                    .eq(TicketOrder::getDeleted, 0)
                    .orderByDesc(TicketOrder::getCreateTime)
                    .last("LIMIT 1")
                    .one();
            if (pending != null) {
                throw new StatusCodeException(StatusCode.PENDING_ORDER_EXISTS, null, String.valueOf(pending.getId()));
            }
        }

        boolean stockDeducted = deductStock(request.getTicketTypeId(), userId, request.getQuantity(), ctx.ticketType.getLimitPerUser());

        Long orderId = redisIDGenerator.getId("ticket_order");
        LocalDateTime now = LocalDateTime.now();
        int timeoutMin = livehouseProperties.getOrder().getPayTimeoutMinutes();
        LocalDateTime expireTime = now.plusMinutes(timeoutMin);

        PriceCalculator.PriceBreakdown price = ctx.price;
        TicketOrder order = new TicketOrder();
        order.setId(orderId);
        order.setOrderNo(String.valueOf(orderId));
        order.setUserId(userId);
        order.setShowId(request.getShowId());
        order.setSessionId(request.getShowId());
        order.setTicketTypeId(request.getTicketTypeId());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(price.getTotalAmount());
        order.setServiceFee(price.getServiceFee());
        order.setMemberDiscountAmount(price.getMemberDiscountAmount());
        order.setDiscountAmount(price.getMemberDiscountAmount());
        order.setPayAmount(price.getPayAmount());
        order.setAmount(price.getPayAmount());
        order.setMemberLevel(price.getMemberLevel());
        order.setOrderStatus(1);
        order.setPayStatus(0);
        order.setExpireTime(expireTime);
        order.setIdempotentKey(request.getIdempotentKey());
        order.setChannel("mp");
        order.setDeleted(0);
        order.setCreateTime(now);
        order.setUpdateTime(now);

        try {
            ticketOrderService.save(order);
            for (Audience a : ctx.audiences) {
                OrderAudience oa = new OrderAudience();
                oa.setOrderId(orderId);
                oa.setAudienceId(a.getId());
                oa.setAudienceName(a.getName());
                oa.setIdCardMask(a.getIdCardMask());
                oa.setCreateTime(now);
                orderAudienceMapper.insert(oa);
            }
        } catch (Exception e) {
            restoreStockOnFailure(request.getTicketTypeId(), userId, request.getQuantity(), stockDeducted);
            throw e;
        }

        stringRedisTemplate.opsForValue().set(userOrderStatusKey, "PENDING", Duration.ofMinutes(timeoutMin + 5L));

        OrderTimeoutMessage timeoutMessage = new OrderTimeoutMessage(
                orderId, userId, request.getTicketTypeId(), request.getQuantity());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messageProducerService.sendOrderTimeoutMessage(timeoutMessage);
                }
            });
        } else {
            messageProducerService.sendOrderTimeoutMessage(timeoutMessage);
        }

        return buildCreateResponse(order);
    }

    private Map<String, Object> buildCreateResponse(TicketOrder order) {
        long remain = 0;
        if (order.getExpireTime() != null) {
            remain = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), order.getExpireTime())); // 0 做兜底，jdk8 时间API
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNo", order.getOrderNo());
        data.put("payAmount", order.getPayAmount());
        data.put("expireTime", order.getExpireTime() == null ? null : order.getExpireTime().format(DT));
        data.put("remainSeconds", remain);
        return data;
    }

    @Override
    public Map<String, Object> list(Long userId, String og, Integer entryStatus, int current, int size) {
        size = Math.min(Math.max(size, 1), 50);
        current = Math.max(current, 1);

        LambdaQueryWrapper<TicketOrder> base = new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .eq(TicketOrder::getDeleted, 0)
                .orderByDesc(TicketOrder::getCreateTime);

        if ("pending".equals(og)) {
            base.eq(TicketOrder::getOrderStatus, 1);
        } else if ("done".equals(og)) {
            base.eq(TicketOrder::getOrderStatus, 2).eq(TicketOrder::getPayStatus, 1);
            if (entryStatus != null) {
                base.eq(TicketOrder::getEntryStatus, entryStatus);
            }
        } else if ("cancel".equals(og)) {
            base.eq(TicketOrder::getOrderStatus, 3);
        } else if ("refunded".equals(og)) {
            base.eq(TicketOrder::getOrderStatus, 4);
        }

        Page<TicketOrder> page = ticketOrderService.page(new Page<>(current, size), base);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(o -> toListItem(o)).collect(Collectors.toList());

        Map<String, Integer> counts = countTabs(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", page.getTotal());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());
        data.put("pages", page.getPages());
        data.put("counts", counts);
        return data;
    }

    @Override
    public Map<String, Object> detail(Long userId, Long orderId) {
        TicketOrder order = loadOwnedOrder(userId, orderId);
        Map<String, Object> item = toListItem(order);
        Show show = showService.getById(order.getShowId());
        Venue venue = show == null ? null : venueMapper.selectById(show.getVenueId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;

        item.put("venueName", venue == null ? null : venue.getName());
        item.put("venueAddress", venue == null ? null : venue.getAddress());
        item.put("showTime", show == null || show.getStartTime() == null ? null : show.getStartTime().format(DT));
        item.put("totalAmount", order.getTotalAmount());
        item.put("serviceFee", order.getServiceFee());
        item.put("memberDiscountAmount", order.getMemberDiscountAmount());
        item.put("payAmount", order.getPayAmount());
        item.put("payStatus", order.getPayStatus());
        item.put("payTime", order.getPayTime() == null ? null : order.getPayTime().format(DT));
        item.put("cover", perf == null ? (show == null ? null : show.getImage()) : perf.getPoster());
        item.put("audiences", buildOrderAudiences(order));
        item.put("refund", buildRefundInfo(order, show));
        return item;
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long orderId, String reason) {
        TicketOrder order = loadOwnedOrder(userId, orderId);
        if (order.getOrderStatus() != 1 || order.getPayStatus() != 0) {
            throw new StatusCodeException(StatusCode.CANCEL_NOT_ALLOWED);
        }
        boolean ok = ticketOrderService.update()
                .set("order_status", 3)
                .set("cancel_time", LocalDateTime.now())
                .set("cancel_reason", StrUtil.blankToDefault(reason, "用户取消"))
                .eq("id", orderId)
                .eq("order_status", 1)
                .eq("pay_status", 0)
                .update();
        if (!ok) {
            throw new StatusCodeException(StatusCode.ORDER_STATUS_ILLEGAL);
        }
        ticketTypeService.update()
                .setSql("left_stock = left_stock + " + order.getQuantity())
                .eq("id", order.getTicketTypeId())
                .update();
        seckillScriptExecutor.executeRestoreStock(order.getTicketTypeId(), userId, order.getQuantity());
        stringRedisTemplate.delete("user:order:status:" + userId + ":" + order.getTicketTypeId());
    }

    @Override
    @Transactional
    public void batchDelete(Long userId, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        ticketOrderService.update()
                .set("deleted", 1)
                .eq("user_id", userId)
                .in("id", orderIds)
                .and(w -> w.in("order_status", 3, 4)
                        .or(q -> q.eq("order_status", 2).eq("pay_status", 1)))
                .update();
    }

    @Override
    @Transactional
    public Map<String, Object> refund(Long userId, Long orderId, String reason) {
        TicketOrder order = loadOwnedOrder(userId, orderId);
        RefundRecord existing = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderId, orderId));
        if (existing != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("refundId", String.valueOf(existing.getId()));
            data.put("refundAmount", existing.getAmount());
            data.put("status", existing.getStatus());
            data.put("statusText", "退款处理中");
            data.put("applyTime", existing.getCreateTime() == null ? null : existing.getCreateTime().format(DT));
            return data;
        }

        Show show = showService.getById(order.getShowId());
        Map<String, Object> refundInfo = buildRefundInfo(order, show);
        if (Boolean.FALSE.equals(refundInfo.get("canRefund"))) {
            String block = (String) refundInfo.get("blockReason");
            StatusCode sc = (StatusCode) refundInfo.get("blockStatusCode");
            if (sc == null) {
                sc = StatusCode.REFUND_NOT_ALLOWED;
            }
            throw new StatusCodeException(sc, block);
        }

        order.setOrderStatus(4);
        order.setPayStatus(2);
        order.setUpdateTime(LocalDateTime.now());
        ticketOrderService.updateById(order);

        electronicTicketMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId)
                .eq(ElectronicTicket::getVerifyStatus, 0)
                .set(ElectronicTicket::getVerifyStatus, 2)); // 已作废（退票）

        ticketTypeService.update()
                .setSql("left_stock = left_stock + " + order.getQuantity())
                .eq("id", order.getTicketTypeId())
                .update();
        seckillScriptExecutor.executeRestoreStock(order.getTicketTypeId(), userId, order.getQuantity());
        stringRedisTemplate.delete("user:order:status:" + userId + ":" + order.getTicketTypeId());

        // 记录退票信息
        RefundRecord record = new RefundRecord();
        record.setId(redisIDGenerator.getId("refund_record"));
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setShowId(order.getShowId());
        record.setTicketTypeId(order.getTicketTypeId());
        record.setQuantity(order.getQuantity());
        record.setAmount(order.getPayAmount());
        record.setStatus(RefundRecord.STATUS_PENDING);
        record.setReason(reason);
        record.setCreateTime(LocalDateTime.now());
        refundRecordMapper.insert(record);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", String.valueOf(record.getId()));
        data.put("refundAmount", record.getAmount());
        data.put("status", record.getStatus());
        data.put("statusText", "退款处理中");
        data.put("applyTime", record.getCreateTime().format(DT));
        return data;
    }

    private TicketOrder loadOwnedOrder(Long userId, Long orderId) {
        TicketOrder order = ticketOrderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId) || Objects.equals(order.getDeleted(), 1)) {
            throw new StatusCodeException(StatusCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private PreviewContext validateAndBuild(Long userId, Long showId, Long ticketTypeId, Integer quantity,
                                            List<Long> audienceIds, boolean throwOnBlock) {
        PreviewContext ctx = new PreviewContext();
        if (showId == null || ticketTypeId == null || quantity == null || quantity <= 0) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }

        Show show = showService.getById(showId);
        TicketType ticketType = ticketTypeService.getById(ticketTypeId);
        if (show == null) {
            throw new StatusCodeException(StatusCode.SHOW_NOT_FOUND);
        }
        if (ticketType == null || !ticketType.getShowId().equals(showId)) {
            throw new StatusCodeException(StatusCode.TICKET_TYPE_NOT_FOUND);
        }

        Venue venue = venueMapper.selectById(show.getVenueId());
        Performance perf = show.getPerformanceId() != null ? performanceMapper.selectById(show.getPerformanceId()) : null;
        ctx.title = perf != null ? perf.getTitle() : show.getTitle();
        ctx.venueName = venue == null ? "" : venue.getName();
        ctx.showTimeText = show.getStartTime() == null ? "" : show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        ctx.ticketType = ticketType;

        LocalDateTime now = LocalDateTime.now();
        if (ticketType.getSaleStartTime() != null && ticketType.getSaleStartTime().isAfter(now)) {
            ctx.blockReason = "未到开售时间";
            if (throwOnBlock) throw new StatusCodeException(StatusCode.SALE_NOT_STARTED);
        }
        if (ticketType.getSaleEndTime() != null && ticketType.getSaleEndTime().isBefore(now)) {
            ctx.blockReason = "已停止售票";
            if (throwOnBlock) throw new StatusCodeException(StatusCode.SALE_ENDED);
        }
        if (ticketType.getLeftStock() == null || ticketType.getLeftStock() < quantity) {
            ctx.blockReason = "库存不足";
            if (throwOnBlock) throw new StatusCodeException(StatusCode.STOCK_NOT_ENOUGH);
        }
        if (ticketType.getPerOrderLimit() != null && quantity > ticketType.getPerOrderLimit()) {
            ctx.blockReason = "超出每单限购";
            if (throwOnBlock) throw new StatusCodeException(StatusCode.LIMIT_PER_ORDER_EXCEEDED);
        }

        int realName = perf != null && perf.getIsRealName() != null ? perf.getIsRealName()
                : (show.getIsRealName() == null ? 1 : show.getIsRealName());
        if (realName == 1) {
            if (audienceIds == null || audienceIds.size() != quantity) {
                ctx.blockReason = "观演人数量与购票数量不一致";
                if (throwOnBlock) throw new StatusCodeException(StatusCode.AUDIENCE_COUNT_MISMATCH);
            }
        }

        List<Audience> audiences = Collections.emptyList();
        if (audienceIds != null && !audienceIds.isEmpty()) {
            audiences = audienceMapper.selectBatchIds(audienceIds);
            if (audiences.size() != audienceIds.size()
                    || audiences.stream().anyMatch(a -> !userId.equals(a.getUserId()))) {
                throw new StatusCodeException(StatusCode.AUDIENCE_NOT_FOUND);
            }
            if (realName == 1) {
                for (Audience a : audiences) {
                    if (a.getAuthStatus() == null || a.getAuthStatus() != 1) {
                        ctx.blockReason = "观演人未完成实名认证";
                        if (throwOnBlock) {
                            throw new StatusCodeException(StatusCode.AUDIENCE_NOT_AUTHENTICATED);
                        }
                    }
                }
            }
        }
        ctx.audiences = audiences;
        ctx.audienceBriefs = audiences.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(a.getId()));
            m.put("name", a.getName());
            m.put("idCardMask", a.getIdCardMask());
            return m;
        }).collect(Collectors.toList());

        UserInfo userInfo = userInfoMapper.selectById(userId);
        int level = userInfo == null || userInfo.getLevel() == null ? 0 : userInfo.getLevel();
        MemberLevel memberLevel = memberLevelMapper.selectById(level);
        ctx.price = priceCalculator.calculate(ticketType.getPrice(), quantity, memberLevel);
        return ctx;
    }

    private boolean deductStock(Long ticketTypeId, Long userId, int quantity, Integer limitPerUser) {
        String stockKey = RedisConstants.TICKET_STOCK_KEY + ticketTypeId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            SeckillScriptExecutor.SeckillResult r = seckillScriptExecutor.executeSeckill(
                    ticketTypeId, userId, quantity, limitPerUser == null ? 99 : limitPerUser);
            if (!r.isSuccess()) {
                if (r == SeckillScriptExecutor.SeckillResult.INSUFFICIENT_STOCK) {
                    throw new StatusCodeException(StatusCode.STOCK_NOT_ENOUGH);
                }
                throw new StatusCodeException(StatusCode.LIMIT_PER_USER_EXCEEDED);
            }
            return true;
        }
        boolean updated = ticketTypeService.update()
                .setSql("left_stock = left_stock - " + quantity)
                .eq("id", ticketTypeId)
                .ge("left_stock", quantity)
                .update();
        if (!updated) {
            throw new StatusCodeException(StatusCode.STOCK_NOT_ENOUGH);
        }
        return false;
    }

    private void restoreStockOnFailure(Long ticketTypeId, Long userId, int quantity, boolean redisDeducted) {
        ticketTypeService.update()
                .setSql("left_stock = left_stock + " + quantity)
                .eq("id", ticketTypeId)
                .update();
        if (redisDeducted) {
            seckillScriptExecutor.executeRestoreStock(ticketTypeId, userId, quantity);
        }
    }

    private Map<String, Object> toListItem(TicketOrder order) {
        Show show = showService.getById(order.getShowId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(order.getTicketTypeId());
        Venue venue = show == null ? null : venueMapper.selectById(show.getVenueId());

        String og = ogCode(order);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", String.valueOf(order.getId()));
        m.put("orderNo", order.getOrderNo());
        m.put("og", og);
        m.put("ogText", ogText(og));
        m.put("entryStatus", order.getEntryStatus());
        m.put("entryStatusText", order.getEntryStatus() != null && order.getEntryStatus() == 1 ? "已核销" : "待入场");
        m.put("statusColor", "done".equals(og) ? "ok" : ("pending".equals(og) ? "brand" : "gray"));
        m.put("title", perf != null ? perf.getTitle() : (show == null ? "" : show.getTitle()));
        m.put("cover", perf != null ? perf.getPoster() : (show == null ? null : show.getImage()));
        m.put("ticketTypeName", tt == null ? "" : tt.getName());
        m.put("quantity", order.getQuantity());
        String info = (tt == null ? "" : tt.getName()) + " × " + order.getQuantity();
        if (show != null && show.getStartTime() != null) {
            info += " · " + show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        }
        if (venue != null) {
            info += " · " + venue.getName();
        }
        m.put("infoText", info);
        m.put("payAmount", order.getPayAmount());
        m.put("createTime", order.getCreateTime() == null ? null : order.getCreateTime().format(DT));
        m.put("expireTime", order.getExpireTime() == null ? null : order.getExpireTime().format(DT));
        long remain = order.getExpireTime() == null ? 0
                : Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), order.getExpireTime()));
        m.put("remainSeconds", order.getOrderStatus() == 1 ? remain : null);
        m.put("canPay", order.getOrderStatus() == 1 && order.getPayStatus() == 0 && remain > 0);
        m.put("canCancel", order.getOrderStatus() == 1 && order.getPayStatus() == 0);
        m.put("canRefund", order.getOrderStatus() == 2 && order.getPayStatus() == 1);
        m.put("canShowTicket", order.getOrderStatus() == 2 && order.getPayStatus() == 1);
        m.put("canDelete", order.getOrderStatus() == 3 || order.getOrderStatus() == 4
                || (order.getOrderStatus() == 2 && order.getEntryStatus() != null && order.getEntryStatus() == 1));

        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, order.getId()));
        m.put("ticketIds", tickets.stream().map(t -> String.valueOf(t.getId())).collect(Collectors.toList()));
        return m;
    }

    private Map<String, Integer> countTabs(Long userId) {
        Map<String, Integer> c = new LinkedHashMap<>();
        c.put("pending", (int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 1)));
        c.put("done", (int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0)
                .eq(TicketOrder::getOrderStatus, 2).eq(TicketOrder::getPayStatus, 1)));
        c.put("cancel", (int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 3)));
        c.put("refunded", (int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 4)));
        return c;
    }

    private List<Map<String, Object>> buildOrderAudiences(TicketOrder order) {
        List<OrderAudience> list = orderAudienceMapper.selectList(new LambdaQueryWrapper<OrderAudience>()
                .eq(OrderAudience::getOrderId, order.getId()));
        return list.stream().map(oa -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", oa.getAudienceName());
            m.put("idCardMask", oa.getIdCardMask());
            m.put("ticketId", oa.getTicketId() == null ? null : String.valueOf(oa.getTicketId()));
            if (oa.getTicketId() != null) {
                ElectronicTicket t = electronicTicketMapper.selectById(oa.getTicketId());
                if (t != null) {
                    m.put("displayCode", t.getDisplayCode());
                }
            }
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> buildRefundInfo(TicketOrder order, Show show) {
        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("policy", "开演前 " + livehouseProperties.getOrder().getRefundDeadlineDays()
                + " 天可退，全额退款，不收手续费");
        refund.put("refundAmount", order.getPayAmount());

        if (order.getOrderStatus() == 4) {
            refund.put("canRefund", false);
            refund.put("blockReason", "已退票");
            return refund;
        }
        if (order.getOrderStatus() != 2 || order.getPayStatus() != 1) {
            refund.put("canRefund", false);
            refund.put("blockReason", "当前状态不可退票");
            return refund;
        }

        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, order.getId()));
        if (tickets.stream().anyMatch(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 1)) {
            refund.put("canRefund", false);
            refund.put("blockReason", "已有票核销，无法退票");
            refund.put("blockStatusCode", StatusCode.REFUND_TICKET_USED);
            return refund;
        }
        if (show != null && show.getStartTime() != null && !show.getStartTime().isAfter(LocalDateTime.now())) {
            refund.put("canRefund", false);
            refund.put("blockReason", "演出已开始，无法退票");
            refund.put("blockStatusCode", StatusCode.REFUND_SHOW_STARTED);
            return refund;
        }
        if (show != null && show.getStartTime() != null) {
            // 1. 算出退票截止的时间点：演出时间 - 7天
            LocalDateTime refundDeadline = show.getStartTime().minusDays(livehouseProperties.getOrder().getRefundDeadlineDays());

            // 2. 如果当前时间已经超过了这个截止点，直接拦截
            if (LocalDateTime.now().isAfter(refundDeadline)) {
                refund.put("canRefund", false);
                refund.put("blockReason", "距开演不足 " + livehouseProperties.getOrder().getRefundDeadlineDays() + " 天，不支持退票");
                refund.put("blockStatusCode", StatusCode.REFUND_WINDOW_CLOSED);
                return refund;
            }
        }
        refund.put("canRefund", true);
        refund.put("blockReason", null);
        refund.put("status", null);
        refund.put("statusText", null);
        refund.put("steps", Collections.emptyList());
        return refund;
    }

    private static String ogCode(TicketOrder order) {
        if (order.getOrderStatus() == 1) return "pending";
        if (order.getOrderStatus() == 2) return "done";
        if (order.getOrderStatus() == 3) return "cancel";
        if (order.getOrderStatus() == 4) return "refunded";
        return "pending";
    }

    private static String ogText(String og) {
        switch (og) {
            case "pending": return "待支付";
            case "done": return "已完成";
            case "cancel": return "已取消";
            case "refunded": return "已退票";
            default: return "";
        }
    }

    private static class PreviewContext {
        TicketType ticketType;
        PriceCalculator.PriceBreakdown price;
        String title;
        String venueName;
        String showTimeText;
        String blockReason;
        List<Audience> audiences = Collections.emptyList();
        List<Map<String, Object>> audienceBriefs = Collections.emptyList();
    }
}