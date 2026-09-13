package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Auther: shaolei
 * @Date: 2026/9/5-11:13
 * @Description：封装Redis缓存问题解决方案工具类(采用函数式编程思想，传递的是数据库查询方法的实现)
 * 情况解释：
 * 1.缓存穿透：请求的数据在redis缓存和数据库中都不存在，因此缓存永远不会生效，永远不会被命中，则请求都打到了数据库，缓存形同虚设，数据库压力暴增。
 * 2.缓存击穿：热点数据访问量大（高并发）且执行业务处理逻辑和数据库等处理操作时间较长，热点数据一旦失效时瞬间会有大量请求打到数据库，压力暴增。
 * 3.缓存雪崩：保存在redis缓存中的大部分缓存key在同一时段内同时失效或者redis服务宕机，许多请求无法被redis命中，因此大量请求会打到数据库，压力暴增。
 */

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // 创建一个10线程数的线程池。
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);


    // 重建缓存方法
    public void set(String key, Object value, Long time, TimeUnit unit){
        // 1.兜底：可能出现的缓存数据与数据库数据不一致问题，及时清除缓存数据。
        // 2.减小缓存雪崩的概率 -> CACHE_SHOP_TTL + RandomUtil.randomLong(0,10);
        // 一定要指定随机数区间，不然会取到负数，会报错：ERR invalid expire time in setex，因为setex 命令不允许传入 ≤0 的过期时间，过期时间必须大于 0 秒。
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time + RandomUtil.randomLong(0,10),unit);
    }

    // 重建带有逻辑过期时间的缓存
    public void setWithLogicExpireTime(String key, Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time))); // 统一转换成秒
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透问题解决方案
    /**
     * 首先查redis中有没有该数据，
     * 命中，有两种情况：
     * 一是这个数据是真实数据，那么就直接返回数据；
     * 二是数据是虚假的(这里是一个空字符串)，那么则直接打回，返回错误提示结果；
     *
     * 未命中，有两种情况：
     * 一是数据库查询不到该数据，那么为该请求的key向redis中存入空缓存数据；
     * 二是查询到数据，重建缓存并返回数据。
     *
     * 优点：实现简单，维护方便。
     * 缺点：1.额外的内存开销
     *      2.短期的数据不一致问题
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,
            Long time,TimeUnit unit){
        // 1.根据商铺唯一id值去Redis数据库中查找商铺信息。
        String key = keyPrefix + id;
        String JsonData = stringRedisTemplate.opsForValue().get(key);

        // 2.如果商铺信息存在于Redis中，那么直接拿取数据并返回结果。
        if (StrUtil.isNotBlank(JsonData)) { // 这个方法只有是有内容的字符串才为true,null、""、"\r\n"都会false。
            return JSONUtil.toBean(JsonData,type);
        }

        // 3.如果这个字符串为""，代表是查到了所缓存的那个数据库不存在的数据，也返回错误提示结果，防止了缓存穿透问题。
        // 如果为null,代表确实没有缓存要访问的数据，那就去查询数据库。
        if(JsonData != null){
            return null;
        }

        // 4.如果Redis中不存在该信息：
        // 4.1 去数据库中查询数据。
        R r = dbFallback.apply(id);
        // 5.如果查找不到店铺信息，就先将一个空字符串保存到Redis中（应对缓存穿透），再返回错误提示结果。
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.如果查找到，就先保存到Redis中作为缓存数据,并设置过期时间，set方法还降低了缓存雪崩的概率。
        this.set(key,r,time,unit);
        // 7.返回结果。
        return r;
    }

    // 缓存击穿问题解决方案 -> 互斥锁
    /**
     * 首先查redis中有没有该数据，
     * 命中，有两种情况：
     * 一是这个数据是真实数据，那么就直接返回数据；
     * 二是数据是虚假的(这里是一个空字符串)，那么则直接打回，返回错误提示结果；
     *
     * 未命中，则拿锁去数据库查询数据，也分两种情况：
     * 如果数据查不到就给redis中存一个这个key(id)对应的空值(空字符串)，并返回错误提示结果；
     * 如果查找到了，那么就将数据存到redis中并返回查到的数据。
     * 拿不到锁，就让后续进来的请求线程重复递归调用该方法(设置睡眠时间防止栈溢出)不断访问redis，直到命中数据为止，返回缓存数据。
     *
     * 缺点：浪费服务器资源，很可能会导致栈溢出，大量请求虽然没有打到数据库，但是都会疯狂开辟栈空间执行本方法。
     */
    public <R,ID> R queryWithMutex(
            String keyPrefix, ID id,Class<R> type,Function<ID,R> dbFallback,
            Long time,TimeUnit unit){
        // 1.根据商铺唯一id值去Redis数据库中查找商铺信息。
        String key = keyPrefix + id;
        String JsonData = stringRedisTemplate.opsForValue().get(key);

        // 2.如果商铺信息存在于Redis中，那么直接拿取数据并返回结果。
        if (StrUtil.isNotBlank(JsonData)) { // 这个方法只有是有内容的字符串才为true,null、""、"\r\n"都会false。
            return JSONUtil.toBean(JsonData,type);
        }

        // 3.如果这个字符串为""，代表是查到了所缓存的那个数据库不存在的数据，也返回错误提示结果，防止了缓存穿透问题。
        // 如果为null,代表确实没有缓存要访问的数据，那就去查询数据库。
        if(JsonData != null){
            return null;
        }

        // 4.如果Redis中不存在该信息：
        // 4.1 就先尝试获取互斥锁，获取到锁才能访问数据库，所以只能通过一个线程。
        String lockKey = LOCK_KEY + id;
        R r = null;
        try{
            boolean isGetLock = tryLock(lockKey);
            // 4.1.1 如果未获取到互斥锁，先睡眠一会，再递归尝试获取锁。
            if(!isGetLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit); // 直到拿到缓存数据后（命中），返回拿到的数据。
            }
            // 4.1.2 获取到互斥锁的线程，去数据库查询数据。
            r = dbFallback.apply(id);
            Thread.sleep(200); // 可去掉，模拟数据库查询数据的缓慢时间，可能也还算上了网络延迟等。

            // 5.如果查找不到店铺信息，就先将一个空字符串保存到Redis中（应对缓存穿透），再返回错误提示结果。
            if(r == null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.如果查找到，就先保存到Redis中作为缓存数据,并设置过期时间。
            this.set(key,r,time,unit);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            // 8.释放互斥锁
            unLock(lockKey);
        }
        // 9.返回结果。
        return r;
    }
    public boolean tryLock(String key){
        // redis中的setex命令，只有当key不存在时才能设置value，成功设置返回1，否则返回0，可以拿来当作互斥锁使用，也设置有效时间，10s。
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",LOCK_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    // 缓存击穿问题解决方案 -> 逻辑过期
    /** 做缓存预热用。
     * 执行这个方法的前提是必须先提前将热点数据从数据库中存入redis，并为数据设置逻辑过期时间字段，以后请求访问redis缓存时会
     * 看是否命中数据，未命中代表访问的数据不是热点数据，因此直接返回错误提示结果，命中时也要判断所访问的数据是否过期，过期了
     * 就委派一个线程拿锁去数据库同步一下数据，保存到redis中（缓存重建），然后返回旧的数据，拿不到锁的线程请求也用旧的数据返回。
     *
     * 缺点：过期时间设置较长，在有效期内读到的数据均是可能的旧数据（如果修改了数据库，那么缓存中的值就是旧值），必须等到逻辑过期了才能
     * 同步数据库数据。过期时间设置较短会频繁去访问数据库，即使该数据没有被修改，做了很多无意义的动作，也挺吃性能。
     */
    public <R,ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,
                                         Long time,TimeUnit unit){
        // 1.根据商铺唯一id值去Redis数据库中查找商铺信息。
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.如果店铺信息不存在于Redis中，说明要访问的数据不是热点数据，则直接返回null，之后会返回错误提示结果。
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 3.否则命中，需要先检查逻辑过期时间字段
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 3.1 如果逻辑过期时间值比当前时间晚，那么就代表没过期，则直接返回缓存数据。
        if(expireTime.isAfter(LocalDateTime.now())){
            return JSONUtil.toBean((JSONObject) redisData.getData(),type); // 这里得到的data数据可能仍是json字符串，再转一次。
        }
        String lockKey = LOCK_KEY + id;
        // 3.2 那如果过期了：
        // 3.3 先尝试获取互斥锁
        boolean isGetLock = tryLock(lockKey);
        // 3.4 成功，那么就委派一个线程去执行缓存重建的任务。
        if(isGetLock) {
            EXECUTOR_SERVICE.submit(() -> {
                // 3.4.1 查询数据库
                R r = dbFallback.apply(id);
                // 3.4.2 重建带有逻辑过期时间的缓存
                this.setWithLogicExpireTime(key,r,time,unit);
            });
        }
        // 3.5 失败，那就拿旧数据直接返回，成功也拿到旧数据并返回（因为交给一个新线程去做了）。
        return JSONUtil.toBean((JSONObject) redisData.getData(),type);
    }
}
