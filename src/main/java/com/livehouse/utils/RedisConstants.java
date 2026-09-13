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

    // 分布式锁相关
    public static final String LOCK_KEY = "lock:";
    public static final Long LOCK_TTL = 10L;

    // 票务秒杀相关
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String TICKET_ORDER_KEY = "ticket:order:";
    
    // 地理位置相关
    public static final String VENUE_GEO_KEY = "venue:geo:";
}