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
import com.livehouse.service.TicketOrderStockService;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.service.app.AppOrderService;
import com.livehouse.service.app.PriceCalculator;
import com.livehouse.service.app.credit.MemberLevelService;
import com.livehouse.vo.app.common.AppPageVo;
import com.livehouse.vo.app.order.OrderAudienceBriefVo;
import com.livehouse.vo.app.order.OrderAudienceDetailVo;
import com.livehouse.vo.app.order.OrderCreateResultVo;
import com.livehouse.vo.app.order.OrderListItemVo;
import com.livehouse.vo.app.order.OrderListVo;
import com.livehouse.vo.app.order.OrderPreviewVo;
import com.livehouse.vo.app.order.OrderRefundApplyVo;
import com.livehouse.vo.app.order.OrderRefundInfoVo;
import com.livehouse.vo.app.order.OrderRefundStepVo;
import com.livehouse.vo.app.order.OrderTabCountsVo;
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
    private MemberLevelService memberLevelService;
    @Resource
    private PriceCalculator priceCalculator;
    @Resource
    private AppMessageService appMessageService;
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
    private TicketOrderStockService ticketOrderStockService;
    @Resource
    private ElectronicTicketMapper electronicTicketMapper;
    @Resource
    private RefundRecordMapper refundRecordMapper;

    @Override
    public OrderPreviewVo preview(Long userId, OrderPreviewRequest request) {
        PreviewContext ctx = validateAndBuild(userId, request.getShowId(), request.getTicketTypeId(),
                request.getQuantity(), request.getAudienceIds(), false);
        PriceCalculator.PriceBreakdown price = ctx.price;

        OrderPreviewVo data = new OrderPreviewVo();
        data.setShowId(String.valueOf(request.getShowId()));
        data.setTitle(ctx.title);
        data.setShowTimeText(ctx.showTimeText);
        data.setVenueName(ctx.venueName);
        data.setTicketTypeName(ctx.ticketType.getName());
        data.setUnitPrice(ctx.ticketType.getPrice());
        data.setQuantity(request.getQuantity());
        data.setTotalAmount(price.getTotalAmount());
        data.setServiceFee(price.getServiceFee());
        data.setMemberLevel(price.getMemberLevel());
        data.setMemberLevelName(price.getMemberLevelName());
        data.setMemberDiscountRate(price.getMemberDiscountRate());
        data.setMemberDiscountAmount(price.getMemberDiscountAmount());
        data.setPayAmount(price.getPayAmount());
        data.setAudiences(ctx.audienceBriefs);
        data.setRemainSeconds(livehouseProperties.getOrder().getPayTimeoutMinutes() * 60L);
        data.setCanBuy(ctx.blockReason == null);
        data.setBlockReason(ctx.blockReason);
        data.setCover(ctx.cover);
        data.setPerformanceId(ctx.performanceId);
        return data;
    }

    @Override
    @Transactional
    public OrderCreateResultVo create(Long userId, OrderCreateRequest request) {
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

        // 防止用户因未支付该票单而重复建单
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
        order.setDbStockDeducted(TicketOrderStockService.dbStockFlag(!stockDeducted));
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

    private OrderCreateResultVo buildCreateResponse(TicketOrder order) {
        long remain = 0;
        if (order.getExpireTime() != null) {
            remain = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), order.getExpireTime()));
        }
        OrderCreateResultVo data = new OrderCreateResultVo();
        data.setOrderId(String.valueOf(order.getId()));
        data.setOrderNo(order.getOrderNo());
        data.setPayAmount(order.getPayAmount());
        data.setExpireTime(order.getExpireTime() == null ? null : order.getExpireTime().format(DT));
        data.setRemainSeconds(remain);
        return data;
    }

    @Override
    public OrderListVo list(Long userId, String og, Integer entryStatus, int current, int size) {
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
        List<OrderListItemVo> records = page.getRecords().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());

        OrderTabCountsVo counts = countTabs(userId);
        return OrderListVo.of(AppPageVo.of(records, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()), counts);
    }

    @Override
    public OrderListItemVo detail(Long userId, Long orderId) {
        TicketOrder order = loadOwnedOrder(userId, orderId);
        OrderListItemVo item = toListItem(order);
        Show show = showService.getById(order.getShowId());
        Venue venue = show == null ? null : venueMapper.selectById(show.getVenueId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;

        item.setVenueName(venue == null ? null : venue.getName());
        item.setVenueAddress(venue == null ? null : venue.getAddress());
        item.setShowTime(show == null || show.getStartTime() == null ? null : show.getStartTime().format(DT));
        item.setShowTimeText(show == null || show.getStartTime() == null ? ""
                : show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
        item.setPerformanceId(perf == null ? null : String.valueOf(perf.getId()));
        item.setTotalAmount(resolveTotalAmount(order));
        item.setServiceFee(order.getServiceFee());
        item.setMemberDiscountAmount(order.getMemberDiscountAmount());
        item.setPayAmount(resolvePayAmount(order));
        item.setPayStatus(order.getPayStatus());
        item.setPayTime(order.getPayTime() == null ? null : order.getPayTime().format(DT));
        item.setCover(resolveShowCover(perf, show));
        item.setAudiences(buildOrderAudiences(order));
        item.setRefund(buildRefundInfo(order, show));
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
        ticketOrderStockService.releaseReservedStock(order);
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
    public OrderRefundApplyVo refund(Long userId, Long orderId, String reason) {
        TicketOrder order = loadOwnedOrder(userId, orderId);
        RefundRecord existing = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderId, orderId));
        if (existing != null) {
            OrderRefundApplyVo data = new OrderRefundApplyVo();
            data.setRefundId(String.valueOf(existing.getId()));
            data.setRefundAmount(existing.getAmount());
            data.setStatus(existing.getStatus());
            data.setStatusText(refundStatusText(existing.getStatus()));
            data.setApplyTime(existing.getCreateTime() == null ? null : existing.getCreateTime().format(DT));
            return data;
        }

        Show show = showService.getById(order.getShowId());
        OrderRefundInfoVo refundInfo = buildRefundInfo(order, show);
        if (Boolean.FALSE.equals(refundInfo.getCanRefund())) {
            throw new StatusCodeException(resolveStatusCode(refundInfo.getBlockStatusCode()), refundInfo.getBlockReason());
        }

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
        appMessageService.insertMessage(userId, "order", "退款申请已提交",
                "退票申请已提交，请等待审核。", "refund", String.valueOf(orderId));

        OrderRefundApplyVo data = new OrderRefundApplyVo();
        data.setRefundId(String.valueOf(record.getId()));
        data.setRefundAmount(record.getAmount());
        data.setStatus(record.getStatus());
        data.setStatusText("退款处理中");
        data.setApplyTime(record.getCreateTime().format(DT));
        return data;
    }

    private static StatusCode resolveStatusCode(Integer code) {
        if (code == null) {
            return StatusCode.REFUND_NOT_ALLOWED;
        }
        for (StatusCode sc : StatusCode.values()) {
            if (code.equals(sc.getCode())) {
                return sc;
            }
        }
        return StatusCode.REFUND_NOT_ALLOWED;
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
        ctx.cover = resolveShowCover(perf, show);
        ctx.performanceId = perf != null ? String.valueOf(perf.getId()) : null;
        ctx.ticketType = ticketType;

        LocalDateTime now = LocalDateTime.now();
        MemberLevel memberLevelForPriority = memberLevelService.resolveMemberLevel(userId);
        int priorityMinutes = memberLevelForPriority.getPriorityMinutes() == null ? 0
                : memberLevelForPriority.getPriorityMinutes();
        if (ticketType.getSaleStartTime() != null) {
            LocalDateTime earliest = ticketType.getSaleStartTime().minusMinutes(priorityMinutes);
            if (now.isBefore(earliest)) {
                if (priorityMinutes > 0) {
                    ctx.blockReason = "会员可提前购票，当前未到你的开售时间";
                    if (throwOnBlock) throw new StatusCodeException(StatusCode.PRIORITY_NOT_REACHED);
                } else {
                    ctx.blockReason = "未到开售时间";
                    if (throwOnBlock) throw new StatusCodeException(StatusCode.SALE_NOT_STARTED);
                }
            } else if (now.isBefore(ticketType.getSaleStartTime())) {
                // 优先购窗口内，允许继续
            }
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
                    if (a.getAuthStatus() == null || (a.getAuthStatus() != 1 && a.getAuthStatus() != 2)) {
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
            OrderAudienceBriefVo m = new OrderAudienceBriefVo();
            m.setId(String.valueOf(a.getId()));
            m.setName(a.getName());
            m.setIdCardMask(a.getIdCardMask());
            return m;
        }).collect(Collectors.toList());

        MemberLevel memberLevel = memberLevelService.resolveMemberLevel(userId);
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

    private BigDecimal resolvePayAmount(TicketOrder order) {
        if (order.getPayAmount() != null) {
            return order.getPayAmount();
        }
        if (order.getAmount() != null) {
            return order.getAmount();
        }
        return order.getTotalAmount();
    }

    private BigDecimal resolveTotalAmount(TicketOrder order) {
        if (order.getTotalAmount() != null) {
            return order.getTotalAmount();
        }
        if (order.getAmount() != null) {
            return order.getAmount();
        }
        return order.getPayAmount();
    }

    private LocalDateTime resolveExpireTime(TicketOrder order) {
        if (order.getExpireTime() != null) {
            return order.getExpireTime();
        }
        if (order.getCreateTime() != null) {
            return order.getCreateTime().plusMinutes(livehouseProperties.getOrder().getPayTimeoutMinutes());
        }
        return null;
    }

    private OrderListItemVo toListItem(TicketOrder order) {
        Show show = showService.getById(order.getShowId());
        Performance perf = show != null && show.getPerformanceId() != null
                ? performanceMapper.selectById(show.getPerformanceId()) : null;
        TicketType tt = ticketTypeService.getById(order.getTicketTypeId());
        Venue venue = show == null ? null : venueMapper.selectById(show.getVenueId());

        String og = ogCode(order);
        OrderListItemVo m = new OrderListItemVo();
        m.setOrderId(String.valueOf(order.getId()));
        m.setOrderNo(order.getOrderNo());
        m.setOg(og);
        m.setOgText(ogText(og));
        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, order.getId()));
        int entryStatus = resolveEntryStatus(order, tickets);
        m.setEntryStatus(entryStatus);
        m.setEntryStatusText(entryStatus == 1 ? "已观演" : "待入场");
        m.setStatusColor("done".equals(og) ? "ok" : ("pending".equals(og) ? "brand" : "gray"));
        m.setTitle(perf != null ? perf.getTitle() : (show == null ? "" : show.getTitle()));
        m.setCover(resolveShowCover(perf, show));
        m.setTicketTypeName(tt == null ? "" : tt.getName());
        m.setQuantity(order.getQuantity());
        String info = (tt == null ? "" : tt.getName()) + " × " + order.getQuantity();
        if (show != null && show.getStartTime() != null) {
            info += " · " + show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        }
        if (venue != null) {
            info += " · " + venue.getName();
        }
        m.setInfoText(info);
        m.setPayAmount(resolvePayAmount(order));
        m.setTotalAmount(resolveTotalAmount(order));
        m.setShowTimeText(show == null || show.getStartTime() == null ? ""
                : show.getStartTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")));
        m.setPerformanceId(perf == null ? null : String.valueOf(perf.getId()));
        m.setShowId(order.getShowId() == null ? null : String.valueOf(order.getShowId()));
        m.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(DT));
        LocalDateTime expireAt = resolveExpireTime(order);
        m.setExpireTime(expireAt == null ? null : expireAt.format(DT));
        long remain = expireAt == null ? 0
                : Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), expireAt));
        m.setRemainSeconds(order.getOrderStatus() == 1 ? remain : null);
        m.setCanPay(order.getOrderStatus() == 1 && order.getPayStatus() == 0 && remain > 0);
        m.setCanCancel(order.getOrderStatus() == 1 && order.getPayStatus() == 0);
        RefundRecord refundRecord = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderId, order.getId()));
        m.setCanRefund(order.getOrderStatus() == 2 && order.getPayStatus() == 1 && refundRecord == null);
        m.setCanShowTicket(order.getOrderStatus() == 2 && order.getPayStatus() == 1 && entryStatus == 0);
        m.setCanDelete(order.getOrderStatus() == 3 || order.getOrderStatus() == 4
                || (order.getOrderStatus() == 2 && entryStatus == 1));

        m.setTicketIds(tickets.stream().map(t -> String.valueOf(t.getId())).collect(Collectors.toList()));
        return m;
    }

    /** 优先按电子票核销状态：本单全部已核销则为 1，与 entry_status 列对齐展示 */
    private int resolveEntryStatus(TicketOrder order, List<ElectronicTicket> tickets) {
        if (tickets != null && !tickets.isEmpty()) {
            boolean allVerified = tickets.stream()
                    .allMatch(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 1);
            if (allVerified) {
                return 1;
            }
        }
        if (order.getEntryStatus() != null && order.getEntryStatus() == 1) {
            return 1;
        }
        return 0;
    }

    private OrderTabCountsVo countTabs(Long userId) {
        OrderTabCountsVo c = new OrderTabCountsVo();
        c.setPending((int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 1)));
        c.setDone((int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0)
                .eq(TicketOrder::getOrderStatus, 2).eq(TicketOrder::getPayStatus, 1)));
        c.setCancel((int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 3)));
        c.setRefunded((int) ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getDeleted, 0).eq(TicketOrder::getOrderStatus, 4)));
        return c;
    }

    private List<OrderAudienceDetailVo> buildOrderAudiences(TicketOrder order) {
        List<OrderAudience> list = orderAudienceMapper.selectList(new LambdaQueryWrapper<OrderAudience>()
                .eq(OrderAudience::getOrderId, order.getId()));
        return list.stream().map(oa -> {
            OrderAudienceDetailVo m = new OrderAudienceDetailVo();
            m.setName(oa.getAudienceName());
            m.setIdCardMask(oa.getIdCardMask());
            m.setTicketId(oa.getTicketId() == null ? null : String.valueOf(oa.getTicketId()));
            if (oa.getTicketId() != null) {
                ElectronicTicket t = electronicTicketMapper.selectById(oa.getTicketId());
                if (t != null) {
                    m.setDisplayCode(t.getDisplayCode());
                    m.setVerifyStatus(t.getVerifyStatus());
                }
            }
            return m;
        }).collect(Collectors.toList());
    }

    private OrderRefundInfoVo buildRefundInfo(TicketOrder order, Show show) {
        OrderRefundInfoVo refund = new OrderRefundInfoVo();
        refund.setPolicy("开演前 " + livehouseProperties.getOrder().getRefundDeadlineDays()
                + " 天可退，全额退款，不收手续费");
        refund.setRefundAmount(order.getPayAmount());

        if (order.getOrderStatus() == 4) {
            RefundRecord doneRecord = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                    .eq(RefundRecord::getOrderId, order.getId()));
            refund.setCanRefund(false);
            refund.setBlockReason("已退票");
            if (doneRecord != null) {
                int rs = doneRecord.getStatus() == null ? RefundRecord.STATUS_SUCCESS : doneRecord.getStatus();
                refund.setStatus(rs);
                refund.setStatusText(refundStatusText(rs));
                refund.setApplyReason(doneRecord.getReason());
                refund.setRejectReason(doneRecord.getAuditRejectReason());
                refund.setApplyTime(doneRecord.getCreateTime() == null ? null : doneRecord.getCreateTime().format(DT));
                refund.setFinishTime(doneRecord.getFinishTime() == null ? null : doneRecord.getFinishTime().format(DT));
                refund.setSteps(buildRefundSteps(doneRecord));
                enrichRefundRecordMeta(refund, doneRecord, order.getId());
            } else {
                refund.setSteps(Collections.emptyList());
            }
            return refund;
        }
        if (order.getOrderStatus() != 2 || order.getPayStatus() != 1) {
            refund.setCanRefund(false);
            refund.setBlockReason("当前状态不可退票");
            return refund;
        }

        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, order.getId()));
        if (tickets.stream().anyMatch(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 1)) {
            refund.setCanRefund(false);
            refund.setBlockReason("已有票核销，无法退票");
            refund.setBlockStatusCode(StatusCode.REFUND_TICKET_USED.getCode());
            return refund;
        }
        if (show != null && show.getStartTime() != null && !show.getStartTime().isAfter(LocalDateTime.now())) {
            refund.setCanRefund(false);
            refund.setBlockReason("演出已开始，无法退票");
            refund.setBlockStatusCode(StatusCode.REFUND_SHOW_STARTED.getCode());
            return refund;
        }
        if (show != null && show.getStartTime() != null) {
            LocalDateTime refundDeadline = show.getStartTime().minusDays(livehouseProperties.getOrder().getRefundDeadlineDays());
            if (LocalDateTime.now().isAfter(refundDeadline)) {
                refund.setCanRefund(false);
                refund.setBlockReason("距开演不足 " + livehouseProperties.getOrder().getRefundDeadlineDays() + " 天，不支持退票");
                refund.setBlockStatusCode(StatusCode.REFUND_WINDOW_CLOSED.getCode());
                return refund;
            }
        }
        RefundRecord record = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderId, order.getId()));
        if (record != null) {
            refund.setCanRefund(false);
            int rs = record.getStatus() == null ? RefundRecord.STATUS_PENDING : record.getStatus();
            refund.setStatus(rs);
            refund.setStatusText(refundStatusText(rs));
            refund.setApplyReason(record.getReason());
            refund.setRejectReason(record.getAuditRejectReason());
            refund.setApplyTime(record.getCreateTime() == null ? null : record.getCreateTime().format(DT));
            refund.setFinishTime(record.getFinishTime() == null ? null : record.getFinishTime().format(DT));
            if (rs == RefundRecord.STATUS_PENDING) {
                refund.setBlockReason("退款审核中");
            } else if (rs == RefundRecord.STATUS_FAILED) {
                refund.setBlockReason(StrUtil.blankToDefault(record.getAuditRejectReason(), "退款未通过"));
            }
            refund.setSteps(buildRefundSteps(record));
            enrichRefundRecordMeta(refund, record, order.getId());
            if (record.getAmount() != null) {
                refund.setRefundAmount(record.getAmount());
            }
            return refund;
        }

        refund.setCanRefund(true);
        refund.setBlockReason(null);
        refund.setStatus(null);
        refund.setStatusText(null);
        refund.setSteps(Collections.emptyList());
        return refund;
    }

    private static String refundStatusText(Integer status) {
        if (status == null) {
            return "退款处理中";
        }
        switch (status) {
            case RefundRecord.STATUS_PENDING:
                return "退款处理中";
            case RefundRecord.STATUS_SUCCESS:
                return "退款成功";
            case RefundRecord.STATUS_FAILED:
                return "退款未通过";
            default:
                return "退款处理中";
        }
    }

    private List<OrderRefundStepVo> buildRefundSteps(RefundRecord record) {
        List<OrderRefundStepVo> steps = new ArrayList<>();
        String applyTime = record.getCreateTime() == null ? "" : record.getCreateTime().format(DT);
        int rs = record.getStatus() == null ? RefundRecord.STATUS_PENDING : record.getStatus();

        OrderRefundStepVo s1 = new OrderRefundStepVo();
        s1.setName("提交退款申请");
        s1.setTime(applyTime);
        s1.setTip(StrUtil.blankToDefault(record.getReason(), null));
        s1.setDone(true);
        steps.add(s1);

        OrderRefundStepVo s2 = new OrderRefundStepVo();
        s2.setName("平台审核");
        if (rs == RefundRecord.STATUS_PENDING) {
            s2.setTime("");
            s2.setTip("预计 1-3 个工作日");
            s2.setDone(false);
        } else {
            s2.setTime(record.getFinishTime() != null ? record.getFinishTime().format(DT) : applyTime);
            s2.setDone(true);
            if (rs == RefundRecord.STATUS_FAILED) {
                s2.setTip(StrUtil.blankToDefault(record.getAuditRejectReason(), "审核未通过"));
            } else {
                s2.setTip("审核通过");
            }
        }
        steps.add(s2);

        if (rs == RefundRecord.STATUS_SUCCESS) {
            OrderRefundStepVo s3 = new OrderRefundStepVo();
            s3.setName("退款到账");
            s3.setTime(record.getFinishTime() == null ? "" : record.getFinishTime().format(DT));
            s3.setTip(record.getFinishTime() != null ? "已原路退回" : "处理中，预计 1-7 个工作日");
            s3.setDone(record.getFinishTime() != null);
            steps.add(s3);
        }
        return steps;
    }

    /** 退款进度页：退款单号、渠道退款单号、电子票真实状态（查库） */
    private void enrichRefundRecordMeta(OrderRefundInfoVo refund, RefundRecord record, Long orderId) {
        refund.setRefundId(String.valueOf(record.getId()));
        if (StrUtil.isNotBlank(record.getChannelRefundNo())) {
            refund.setChannelRefundNo(record.getChannelRefundNo());
            refund.setRefundNo(record.getChannelRefundNo());
        } else {
            refund.setRefundNo(String.valueOf(record.getId()));
        }
        List<ElectronicTicket> tickets = electronicTicketMapper.selectList(new LambdaQueryWrapper<ElectronicTicket>()
                .eq(ElectronicTicket::getOrderId, orderId));
        refund.setTicketCount(tickets.size());
        refund.setTicketStatusText(describeRefundTicketStatus(tickets, record.getStatus()));
        long voidCount = tickets.stream()
                .filter(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 2)
                .count();
        refund.setTicketVoidCount((int) voidCount);
    }

    private static String describeRefundTicketStatus(List<ElectronicTicket> tickets, Integer refundStatus) {
        if (tickets == null || tickets.isEmpty()) {
            return "暂无电子票";
        }
        int total = tickets.size();
        long verified = tickets.stream()
                .filter(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 1)
                .count();
        long voided = tickets.stream()
                .filter(t -> t.getVerifyStatus() != null && t.getVerifyStatus() == 2)
                .count();
        long valid = tickets.stream()
                .filter(t -> t.getVerifyStatus() == null || t.getVerifyStatus() == 0)
                .count();
        if (verified > 0 && verified == total) {
            return "已核销（" + total + " 张）";
        }
        if (voided == total) {
            return "已作废（" + total + " 张）";
        }
        if (refundStatus != null && refundStatus == RefundRecord.STATUS_PENDING && valid > 0) {
            return "退款审核中，票仍有效（" + valid + " 张）";
        }
        if (refundStatus != null && refundStatus == RefundRecord.STATUS_FAILED && valid > 0) {
            return "待使用（" + valid + " 张）";
        }
        if (valid > 0 && voided > 0) {
            return "待使用 " + valid + " 张，已作废 " + voided + " 张";
        }
        if (valid == total) {
            return "待使用（" + total + " 张）";
        }
        return "待使用（" + total + " 张）";
    }

    private static String ogCode(TicketOrder order) {
        if (order.getOrderStatus() == 1) return "pending";
        if (order.getOrderStatus() == 2) return "done";
        if (order.getOrderStatus() == 3) return "cancel";
        if (order.getOrderStatus() == 4) return "refunded";
        return "pending";
    }

    private static String resolveShowCover(Performance perf, Show show) {
        if (perf != null && StrUtil.isNotBlank(perf.getPoster())) {
            return perf.getPoster();
        }
        return show == null ? null : show.getImage();
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
        String cover;
        String performanceId;
        String blockReason;
        List<Audience> audiences = Collections.emptyList();
        List<OrderAudienceBriefVo> audienceBriefs = Collections.emptyList();
    }
}