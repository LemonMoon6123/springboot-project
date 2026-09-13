package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Auther: shaolei
 * @Date: 2026/9/6-13:59
 * @Description：
 */

public class SimpleRedisLock {
    private String name; // 业务名
    private StringRedisTemplate stringRedisTemplate;
    private final static String LOCK_KEY_PREFIX = "lock:";
    // 利用UUID区别集群下多个线程可能出现重复问题。
    private final static String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 获取锁

    /**
     * 使用Redis的setnx命令实现互斥效果，利用这个互斥特性实现分布式互斥锁。
     * 同时设置锁的过期时间timeoutSec，防止Redis宕机或者线程执行业务时间过长时，针对key的及时销毁，及时销毁锁，防止死锁产生。
     * Redis底层实现命令：set key value ex timeoutSec nx
     */
    public boolean tryLock(long timeoutSec){
        // 为锁对象增加线程标识，为释放锁对象时做判断是否是自己的锁对象。
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // Redis命令： set key value ex timeoutSec nx
        Boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(LOCK_KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    // 释放锁

    /**
     * Redis底层命令：del key
     */
    public void unlock(){
        // 1.获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 2.从缓存中查找锁对象的值
        String id = stringRedisTemplate.opsForValue().get(LOCK_KEY_PREFIX + name);
        // 3.如果是当前线程的锁对象，则释放锁，否则什么也不做。
        if(threadId.equals(id)){
            // 释放锁
            stringRedisTemplate.delete(LOCK_KEY_PREFIX + name);
        }
    }

}
