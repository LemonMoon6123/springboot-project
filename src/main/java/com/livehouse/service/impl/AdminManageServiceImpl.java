package com.livehouse.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.livehouse.admin.AdminModuleMeta;
import com.livehouse.admin.AdminModuleRegistry;
import com.livehouse.entity.Artist;
import com.livehouse.entity.Audience;
import com.livehouse.entity.CheckInRecord;
import com.livehouse.entity.CreditExchange;
import com.livehouse.entity.CreditGoods;
import com.livehouse.entity.CreditLog;
import com.livehouse.entity.ElectronicTicket;
import com.livehouse.entity.Favorite;
import com.livehouse.entity.MemberLevel;
import com.livehouse.entity.Message;
import com.livehouse.entity.Notice;
import com.livehouse.entity.OrderAudience;
import com.livehouse.entity.Payment;
import com.livehouse.entity.Performance;
import com.livehouse.entity.RealNameAuth;
import com.livehouse.entity.RefundRecord;
import com.livehouse.entity.Show;
import com.livehouse.entity.ShowComment;
import com.livehouse.entity.TicketOrder;
import com.livehouse.entity.TicketType;
import com.livehouse.entity.User;
import com.livehouse.entity.UserInfo;
import com.livehouse.entity.Venue;
import com.livehouse.entity.VenueStaff;
import com.livehouse.mapper.ArtistMapper;
import com.livehouse.mapper.AudienceMapper;
import com.livehouse.mapper.CheckInRecordMapper;
import com.livehouse.mapper.CreditExchangeMapper;
import com.livehouse.mapper.CreditGoodsMapper;
import com.livehouse.mapper.CreditLogMapper;
import com.livehouse.mapper.ElectronicTicketMapper;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.mapper.MemberLevelMapper;
import com.livehouse.mapper.MessageMapper;
import com.livehouse.mapper.NoticeMapper;
import com.livehouse.mapper.OrderAudienceMapper;
import com.livehouse.mapper.PaymentMapper;
import com.livehouse.mapper.PerformanceMapper;
import com.livehouse.mapper.RealNameAuthMapper;
import com.livehouse.mapper.RefundRecordMapper;
import com.livehouse.mapper.ShowCommentMapper;
import com.livehouse.mapper.ShowMapper;
import com.livehouse.mapper.TicketOrderMapper;
import com.livehouse.mapper.TicketTypeMapper;
import com.livehouse.mapper.UserInfoMapper;
import com.livehouse.mapper.UserMapper;
import com.livehouse.mapper.VenueMapper;
import com.livehouse.mapper.VenueStaffMapper;
import com.livehouse.service.IAdminManageService;
import com.livehouse.service.RefundFulfillmentService;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.service.map.AmapGeocodeService;
import com.livehouse.util.PerformanceDetailImages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminManageServiceImpl implements IAdminManageService {

    @Value("${livehouse.upload.dir:frontend-livehouse/uploads}")
    private String uploadDir;

    @Value("${livehouse.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    @Autowired
    private AmapGeocodeService amapGeocodeService;

    @Autowired
    private RefundFulfillmentService refundFulfillmentService;

    @Autowired
    private AppMessageService appMessageService;

    @Autowired
    private PerformanceMapper performanceMapper;
    @Autowired
    private ShowMapper showMapper;
    @Autowired
    private TicketTypeMapper ticketTypeMapper;
    @Autowired
    private ArtistMapper artistMapper;
    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private VenueMapper venueMapper;
    @Autowired
    private VenueStaffMapper venueStaffMapper;
    @Autowired
    private TicketOrderMapper ticketOrderMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private ElectronicTicketMapper electronicTicketMapper;
    @Autowired
    private RefundRecordMapper refundRecordMapper;
    @Autowired
    private OrderAudienceMapper orderAudienceMapper;
    @Autowired
    private CheckInRecordMapper checkInRecordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    private AudienceMapper audienceMapper;
    @Autowired
    private RealNameAuthMapper realNameAuthMapper;
    @Autowired
    private FavoriteMapper favoriteMapper;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ShowCommentMapper showCommentMapper;
    @Autowired
    private CreditGoodsMapper creditGoodsMapper;
    @Autowired
    private CreditLogMapper creditLogMapper;
    @Autowired
    private CreditExchangeMapper creditExchangeMapper;
    @Autowired
    private MemberLevelMapper memberLevelMapper;

    private final Map<String, ModuleBinding> bindings = new HashMap<>();

    @PostConstruct
    public void initBindings() {
        bind("performance", performanceMapper, Performance.class);
        bind("show", showMapper, Show.class);
        bind("ticket_type", ticketTypeMapper, TicketType.class);
        bind("artist", artistMapper, Artist.class);
        bind("notice", noticeMapper, Notice.class);
        bind("venue", venueMapper, Venue.class);
        bind("venue_staff", venueStaffMapper, VenueStaff.class);
        bind("ticket_order", ticketOrderMapper, TicketOrder.class);
        bind("payment", paymentMapper, Payment.class);
        bind("electronic_ticket", electronicTicketMapper, ElectronicTicket.class);
        bind("refund_record", refundRecordMapper, RefundRecord.class);
        bind("order_audience", orderAudienceMapper, OrderAudience.class);
        bind("check_in_record", checkInRecordMapper, CheckInRecord.class);
        bind("user", userMapper, User.class);
        bind("user_info", userInfoMapper, UserInfo.class);
        bind("audience", audienceMapper, Audience.class);
        bind("real_name_auth", realNameAuthMapper, RealNameAuth.class);
        bind("favorite", favoriteMapper, Favorite.class);
        bind("message", messageMapper, Message.class);
        bind("show_comment", showCommentMapper, ShowComment.class);
        bind("credit_goods", creditGoodsMapper, CreditGoods.class);
        bind("credit_log", creditLogMapper, CreditLog.class);
        bind("credit_exchange", creditExchangeMapper, CreditExchange.class);
        bind("member_level", memberLevelMapper, MemberLevel.class);
    }

    private void bind(String moduleKey, BaseMapper<?> mapper, Class<?> entityClass) {
        AdminModuleRegistry.require(moduleKey);
        bindings.put(moduleKey, new ModuleBinding(mapper, entityClass));
    }

    @Override
    public List<AdminModuleMeta> listModules() {
        return AdminModuleRegistry.all();
    }

    @Override
    public IPage<?> page(String module, long current, long size, String keyword) {
        ModuleBinding binding = requireBinding(module);
        AdminModuleMeta meta = AdminModuleRegistry.require(module);

        current = Math.max(current, 1);
        size = Math.min(Math.max(size, 1), 100);

        Page<Object> page = new Page<>(current, size);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(keyword) && StrUtil.isNotBlank(meta.getSearchField())) {
            String column = resolveDbColumn(binding.getEntityClass(), meta.getSearchField());
            wrapper.like(column, keyword.trim());
        }
        if ("ticket_type".equals(module)) {
            wrapper.orderByDesc(resolveDbColumn(binding.getEntityClass(), "id"));
        } else {
            applyDefaultOrder(binding.getEntityClass(), wrapper);
        }

        @SuppressWarnings("unchecked")
        BaseMapper<Object> mapper = (BaseMapper<Object>) binding.getMapper();
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public Object getById(String module, Serializable id) {
        ModuleBinding binding = requireBinding(module);
        return binding.getMapper().selectById(id);
    }

    @Override
    public boolean create(String module, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        sanitizeBody(body);
        ModuleBinding binding = requireBinding(module);
        enrichBeforeSave(module, body);
        enrichVenueGeocode(module, body, null);
        List<String> performanceDetailUrls = extractPerformanceDetailUrls(module, body);
        Object entity = BeanUtil.toBean(body, binding.getEntityClass());
        applyCreateDefaults(module, entity);
        @SuppressWarnings("unchecked")
        BaseMapper<Object> mapper = (BaseMapper<Object>) binding.getMapper();
        boolean ok = mapper.insert(entity) > 0;
        if (ok && "performance".equals(module)) {
            persistPerformanceDetailImages((Performance) entity, performanceDetailUrls);
        }
        return ok;
    }

    @Override
    @Transactional
    public boolean update(String module, Serializable id, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        sanitizeBody(body);
        ModuleBinding binding = requireBinding(module);
        Object existing = binding.getMapper().selectById(id);
        if (existing == null) {
            return false;
        }
        Integer priorRefundStatus = null;
        if ("refund_record".equals(module) && existing instanceof RefundRecord) {
            priorRefundStatus = ((RefundRecord) existing).getStatus();
        }
        enrichBeforeSave(module, body);
        enrichVenueGeocode(module, body, existing instanceof Venue ? (Venue) existing : null);
        List<String> performanceDetailUrls = extractPerformanceDetailUrls(module, body);
        BeanUtil.fillBeanWithMap(body, existing, false, CopyOptions.create().ignoreNullValue());
        if ("performance".equals(module)) {
            persistPerformanceDetailImages((Performance) existing, performanceDetailUrls);
        }
        setPrimaryKey(existing, binding.getEntityClass(), id);
        @SuppressWarnings("unchecked")
        BaseMapper<Object> mapper = (BaseMapper<Object>) binding.getMapper();
        boolean ok = mapper.updateById(existing) > 0;
        if (ok && "refund_record".equals(module) && existing instanceof RefundRecord) {
            RefundRecord record = (RefundRecord) existing;
            Integer newStatus = record.getStatus();
            if (priorRefundStatus != null && priorRefundStatus == RefundRecord.STATUS_PENDING
                    && newStatus != null && !newStatus.equals(priorRefundStatus)) {
                if (newStatus == RefundRecord.STATUS_SUCCESS) {
                    refundFulfillmentService.onAuditApproved(record);
                } else if (newStatus == RefundRecord.STATUS_FAILED) {
                    String rejectTip = StrUtil.blankToDefault(record.getAuditRejectReason(), "审核未通过");
                    appMessageService.insertMessage(record.getUserId(), "order", "退款未通过",
                            rejectTip, "refund", String.valueOf(record.getOrderId()));
                }
            }
        }
        return ok;
    }

    @Override
    public List<Map<String, Object>> selectOptions(String module, String keyword, int limit) {
        int cap = Math.min(Math.max(limit, 1), 500);
        String kw = StrUtil.trim(keyword);
        switch (module) {
            case "performance": {
                QueryWrapper<Performance> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.and(w -> w.like("title", kw).or().like("artist_name", kw).or().like("type", kw));
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (Performance p : performanceMapper.selectList(q)) {
                    out.add(option(p.getId(), joinLabel(p.getTitle(), p.getType())));
                }
                return out;
            }
            case "show": {
                QueryWrapper<Show> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.and(w -> w.like("title", kw).or().like("artist", kw).or().like("session_label", kw));
                }
                q.orderByDesc("start_time").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
                for (Show show : showMapper.selectList(q)) {
                    String tail = show.getSessionLabel();
                    if (StrUtil.isBlank(tail) && show.getStartTime() != null) {
                        tail = show.getStartTime().format(fmt);
                    }
                    out.add(option(show.getId(), joinLabel(show.getTitle(), tail)));
                }
                return out;
            }
            case "venue": {
                QueryWrapper<Venue> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.and(w -> w.like("name", kw).or().like("city", kw));
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (Venue v : venueMapper.selectList(q)) {
                    out.add(option(v.getId(), joinLabel(v.getName(), v.getCity())));
                }
                return out;
            }
            case "artist": {
                QueryWrapper<Artist> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.like("name", kw);
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (Artist a : artistMapper.selectList(q)) {
                    out.add(option(a.getId(), joinLabel(a.getName(), a.getCity())));
                }
                return out;
            }
            case "user": {
                QueryWrapper<User> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.and(w -> w.like("phone", kw).or().like("nick_name", kw));
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (User user : userMapper.selectList(q)) {
                    String nick = StrUtil.blankToDefault(user.getNickName(), "用户");
                    out.add(option(user.getId(), joinLabel(nick, user.getPhone())));
                }
                return out;
            }
            case "ticket_order": {
                QueryWrapper<TicketOrder> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.like("order_no", kw);
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (TicketOrder order : ticketOrderMapper.selectList(q)) {
                    out.add(option(order.getId(), joinLabel(order.getOrderNo(), "#" + order.getId())));
                }
                return out;
            }
            case "credit_goods": {
                QueryWrapper<CreditGoods> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.like("name", kw);
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (CreditGoods g : creditGoodsMapper.selectList(q)) {
                    out.add(option(g.getId(), g.getName()));
                }
                return out;
            }
            case "electronic_ticket": {
                QueryWrapper<ElectronicTicket> q = new QueryWrapper<>();
                if (StrUtil.isNotBlank(kw)) {
                    q.and(w -> w.like("display_code", kw).or().like("short_code", kw));
                }
                q.orderByDesc("id").last("LIMIT " + cap);
                List<Map<String, Object>> out = new ArrayList<>();
                for (ElectronicTicket t : electronicTicketMapper.selectList(q)) {
                    out.add(option(t.getId(), joinLabel(t.getDisplayCode(), "#" + t.getId())));
                }
                return out;
            }
            default:
                throw new IllegalArgumentException("不支持选项列表: " + module);
        }
    }

    private static Map<String, Object> option(Object value, String label) {
        Map<String, Object> m = new LinkedHashMap<>(2);
        m.put("value", value);
        m.put("label", label);
        return m;
    }

    private static String joinLabel(String main, String extra) {
        if (StrUtil.isBlank(main)) {
            return StrUtil.blankToDefault(extra, "—");
        }
        if (StrUtil.isBlank(extra)) {
            return main;
        }
        return main + " · " + extra;
    }

    private static void sanitizeBody(Map<String, Object> body) {
        body.remove("id");
        body.remove("createTime");
        body.remove("updateTime");
    }

    private void enrichVenueGeocode(String module, Map<String, Object> body, Venue existing) {
        if (!"venue".equals(module)) {
            return;
        }
        if (hasCoord(body.get("longitude")) && hasCoord(body.get("latitude"))) {
            return;
        }
        boolean addressTouched = body.containsKey("address") || body.containsKey("city");
        String address = body.containsKey("address")
                ? StrUtil.trimToNull(String.valueOf(body.get("address")))
                : (existing != null ? existing.getAddress() : null);
        String city = body.containsKey("city")
                ? StrUtil.trimToNull(String.valueOf(body.get("city")))
                : (existing != null ? existing.getCity() : null);
        if (StrUtil.isBlank(address)) {
            return;
        }
        if (!addressTouched && existing != null
                && existing.getLongitude() != null && existing.getLatitude() != null) {
            return;
        }
        amapGeocodeService.geocode(city, address).ifPresent(coords -> {
            body.put("longitude", coords[0]);
            body.put("latitude", coords[1]);
        });
    }

    private static boolean hasCoord(Object v) {
        if (v == null || StrUtil.isBlank(String.valueOf(v))) {
            return false;
        }
        try {
            Double.parseDouble(String.valueOf(v).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void enrichBeforeSave(String module, Map<String, Object> body) {
        if ("performance".equals(module) && body.containsKey("artistId")) {
            Long artistId = toLong(body.get("artistId"));
            if (artistId == null) {
                body.put("artistName", null);
            } else {
                Artist artist = artistMapper.selectById(artistId);
                if (artist != null) {
                    body.put("artistName", artist.getName());
                }
            }
        }
        if ("show".equals(module) && body.containsKey("artistId")) {
            Long artistId = toLong(body.get("artistId"));
            if (artistId == null) {
                body.put("artist", null);
            } else {
                Artist artist = artistMapper.selectById(artistId);
                if (artist != null) {
                    body.put("artist", artist.getName());
                }
            }
        }
        if ("refund_record".equals(module) && body.containsKey("status")) {
            int st = toInt(body.get("status"), -1);
            if (st == RefundRecord.STATUS_FAILED) {
                String reject = body.get("auditRejectReason") == null ? null : String.valueOf(body.get("auditRejectReason"));
                if (StrUtil.isBlank(StrUtil.trim(reject))) {
                    throw new IllegalArgumentException("审核不通过须填写不通过理由");
                }
                if (!body.containsKey("finishTime")) {
                    body.put("finishTime", LocalDateTime.now());
                }
            }
        }
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPerformanceDetailUrls(String module, Map<String, Object> body) {
        if (!"performance".equals(module) || !body.containsKey("detailImages")) {
            return null;
        }
        Object raw = body.remove("detailImages");
        return (List<String>) PerformanceDetailImages.coerceToUrlList(raw);
    }

    private void persistPerformanceDetailImages(Performance performance, List<String> urls) {
        if (performance == null || performance.getId() == null || urls == null) {
            return;
        }
        if (urls.isEmpty()) {
            performance.setDetailImages("[]");
            return;
        }
        try {
            List<String> normalized = PerformanceDetailImages.normalizeUrls(
                    performance.getId(), urls, resolveUploadDir(), urlPrefix);
            performance.setDetailImages(PerformanceDetailImages.toJson(normalized));
        } catch (IOException e) {
            throw new IllegalStateException("详情长图保存失败：" + e.getMessage(), e);
        }
    }

    private Path resolveUploadDir() {
        Path path = Paths.get(uploadDir);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private static void applyCreateDefaults(String module, Object entity) {
        if (entity instanceof TicketType) {
            TicketType tt = (TicketType) entity;
            if (tt.getSortNo() == null) {
                tt.setSortNo(0);
            }
            if (tt.getLeftStock() == null && tt.getTotalStock() != null) {
                tt.setLeftStock(tt.getTotalStock());
            }
        }
        if (entity instanceof Notice) {
            Notice notice = (Notice) entity;
            if (notice.getSortNo() == null) {
                notice.setSortNo(0);
            }
        }
        if (entity instanceof Performance) {
            Performance perf = (Performance) entity;
            if (perf.getSessionCount() == null) {
                perf.setSessionCount(0);
            }
            if (perf.getWantCount() == null) {
                perf.setWantCount(0);
            }
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean delete(String module, Serializable id) {
        ModuleBinding binding = requireBinding(module);
        return binding.getMapper().deleteById(id) > 0;
    }

    @Override
    public boolean updateStatus(String module, Serializable id, Object status) {
        AdminModuleMeta meta = AdminModuleRegistry.require(module);
        if (StrUtil.isBlank(meta.getStatusField())) {
            throw new IllegalArgumentException("模块不支持状态变更: " + module);
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        ModuleBinding binding = requireBinding(module);
        Object existing = binding.getMapper().selectById(id);
        if (existing == null) {
            return false;
        }
        ReflectUtil.setFieldValue(existing, meta.getStatusField(), status);
        @SuppressWarnings("unchecked")
        BaseMapper<Object> mapper = (BaseMapper<Object>) binding.getMapper();
        return mapper.updateById(existing) > 0;
    }

    private ModuleBinding requireBinding(String module) {
        AdminModuleRegistry.require(module);
        ModuleBinding binding = bindings.get(module);
        if (binding == null) {
            throw new IllegalStateException("模块未绑定 Mapper: " + module);
        }
        return binding;
    }

    private static void applyDefaultOrder(Class<?> entityClass, QueryWrapper<Object> wrapper) {
        if (ReflectUtil.hasField(entityClass, "createTime")) {
            wrapper.orderByDesc(resolveDbColumn(entityClass, "createTime"));
            return;
        }
        if (ReflectUtil.hasField(entityClass, "checkTime")) {
            wrapper.orderByDesc(resolveDbColumn(entityClass, "checkTime"));
            return;
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo != null) {
            wrapper.orderByDesc(tableInfo.getKeyColumn());
        }
    }

    /** 元数据 searchField 为 Java 属性名，QueryWrapper 需用数据库列名 */
    private static String resolveDbColumn(Class<?> entityClass, String property) {
        if (StrUtil.isBlank(property)) {
            return property;
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo != null) {
            if (property.equals(tableInfo.getKeyProperty())) {
                return tableInfo.getKeyColumn();
            }
            for (TableFieldInfo field : tableInfo.getFieldList()) {
                if (property.equals(field.getProperty())) {
                    return field.getColumn();
                }
            }
        }
        return StrUtil.toUnderlineCase(property);
    }

    private static void setPrimaryKey(Object entity, Class<?> entityClass, Serializable id) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            return;
        }
        ReflectUtil.setFieldValue(entity, tableInfo.getKeyProperty(), id);
    }

    private static final class ModuleBinding {
        private final BaseMapper<?> mapper;
        private final Class<?> entityClass;

        private ModuleBinding(BaseMapper<?> mapper, Class<?> entityClass) {
            this.mapper = mapper;
            this.entityClass = entityClass;
        }

        public BaseMapper<?> getMapper() {
            return mapper;
        }

        public Class<?> getEntityClass() {
            return entityClass;
        }
    }
}