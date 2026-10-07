package com.livehouse.utils;

public class RedisConstants {
    // 用户登录相关
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    // 演出缓存相关
    public static final Long CACHE_SHOW_TTL = 30L;
    public static final String CACHE_SHOW_KEY = "cache:show:";
    
    // 场馆缓存相关
    public static final Long CACHE_VENUE_TTL = 30L;
    public static final String CACHE_VENUE_KEY = "cache:venue:";

    // 票种缓存相关（按演出ID缓存该演出下的票种列表）
    public static final Long CACHE_TICKET_TYPE_TTL = 30L;
    public static final String CACHE_TICKET_TYPE_KEY = "cache:tickettype:show:";

    // 分布式锁相关
    public static final String LOCK_KEY = "lock:";
    public static final Long LOCK_TTL = 10L;

    // 场馆读写锁（双写一致：读共享、写互斥）
    public static final String LOCK_VENUE_RW_KEY = "lock:rw:venue:";

    // 场馆布隆过滤器（防缓存穿透）
    public static final String BLOOM_VENUE_KEY = "bloom:venue";

    // 票务秒杀相关
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String TICKET_ORDER_KEY = "ticket:order:";
    
    // 地理位置相关
    public static final String VENUE_GEO_KEY = "venue:geo:";

    public static final Long SECKILL_IDEMPOTENT_TTL = 24L; // 单位：小时

    // 退票库存归还幂等（退票记录可能被定时任务重复投递，归还动作需要防重）
    public static final String REFUND_RESTORED_KEY = "idem:refund:restored:";
}