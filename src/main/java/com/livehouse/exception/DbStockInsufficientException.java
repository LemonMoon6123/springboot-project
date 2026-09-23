package com.livehouse.exception;

/**
 * 数据库库存不足：确定性业务失败。
 * <p>
 * Redis Lua 已通过但 MySQL CAS 扣减失败，通常意味着 Redis 库存已相对 DB 虚高。
 * 对此类异常不应框架重试，而应校准 Redis 后确认消息。
 */
public class DbStockInsufficientException extends CustomException {

    public DbStockInsufficientException(String message) {
        super(message);
    }
}
