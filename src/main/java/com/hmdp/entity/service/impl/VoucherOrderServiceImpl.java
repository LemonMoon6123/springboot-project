package com.hmdp.entity.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.service.ISeckillVoucherService;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.entity.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDGenerater;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDGenerater redisIDGenerater;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    // 创建一个新的线程对象
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 创建一个新线程需要执行的任务 (使用内部类去实现一个Runnable接口)
    private class VoucherOrderHandler implements Runnable{

        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true){
                try {
                    // 1.不断地从消息队列中拿取订单信息 XREADGROUP CREATE group g1 c1 count 1 block 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> messages = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2. 如果读取不到消息，则继续下一次循环
                    if(messages == null || messages.isEmpty()){
                        continue;
                    }
                    // 3. 获取出消息中的订单相关信息
                    MapRecord<String, Object, Object> target = messages.get(0);
                    Map<Object, Object> orderMessage = target.getValue();
                    RecordId messageId = target.getId();
                    // 4. 将Map集合转换成Bean对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMessage, new VoucherOrder(), true);
                    // 5. 处理订单
                    handleVoucherOrder(voucherOrder);
                    // 6. 最后确认消息 XACK key(队列key) group id(消息id)
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",messageId);
                } catch (Exception e) {
                    log.error("处理订单信息异常： ",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList(){
            while(true){
                try {
                    // 6. 如果发生意外，则从pending-list中取出已消费但未确认的消息 XREADGROUP CREATE group g1 c1 count 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> nACKMessage = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 7. 如果查不到未消费的消息，代表pending-list中没有需要确的消息，退出循环
                    if(nACKMessage == null || nACKMessage.isEmpty()){
                        break;
                    }
                    // 8. 处理未完成确认的消息
                    // 3. 获取出消息中的订单相关信息
                    MapRecord<String, Object, Object> target = nACKMessage.get(0);
                    Map<Object, Object> orderMessage = target.getValue();
                    RecordId messageId = target.getId();
                    // 4. 将Map集合转换成Bean对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMessage, new VoucherOrder(), true);
                    // 5. 处理订单
                    handleVoucherOrder(voucherOrder);
                    // 6. 最后确认消息 XACK key(队列key) group id(消息id)
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",messageId);
                }catch (Exception e){
                    log.error("处理订单信息异常： ",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }


    @PostConstruct // 本类一初始化，就会立即执行这个方法，从而一开始就执行一个新的线程任务。
    private void init(){
        String queueName = "stream.orders";
        try {
            // XGROUP CREATE stream.orders g1 0 MKSTREAM
            // MKSTREAM：stream不存在就自动创建空stream
            stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.from("0"), "g1");
        } catch (Exception e) {
            log.info("消费者组 g1 已存在，跳过创建");
        }
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 7.创建分布式锁对象 - key的构成："lock:业务名:用户id:秒杀券id"，这个设计很关键，决定能否正确执行秒杀业务。
        RLock lock = redissonClient.getLock("lock:order:" + userId + ":" + voucherId);
        // 8.尝试获取锁对象
        boolean getLock = lock.tryLock();
        if(!getLock){
            log.error("不允许重复下单！");
            return;
        }
        try {
            // 9. 创建订单
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 10.释放锁对象
            lock.unlock();
        }
    }

    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIDGenerater.getId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), voucherId.toString(), userId.toString(),String.valueOf(orderId));
        // 2.判断结果是否为0
        int num = result.intValue();
        // 2.1 不为0，代表没有购买资格
        if(num != 0){
            return Result.fail(num == 1 ?"库存不足！":"您最多只能购买一单！");
        }
        // 4.获取当前类的代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        // 5.返回订单id
        return Result.ok(orderId);
    }

    /*public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        // 2.判断结果是否为0
        int num = result.intValue();
        // 2.1 不为0，代表没有购买资格
        if(num != 0){
            return Result.fail(num == 1 ?"库存不足！":"您最多只能购买一单！");
        }
        // 2.2 为0，有购买资格，先创建订单信息。
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIDGenerater.getId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        // 3.将订单信息加入阻塞队列中，让一个新线程去异步执行这个任务。
        blockingQueue.add(voucherOrder);

        // 4.获取当前类的代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        // 5.返回订单id
        return Result.ok(orderId);
    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 6.不为空，扣减库存,利用CAS法，配合数据库写操作的原子性（行锁），每次在执行更新操作时检查是否满足条件，以解决超卖问题。
        // 规定：同一个用户，同一张券一次只能购买一张。
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            log.error("用户已经购买过一次！");
            return;
        }
        // 减库存，解决超卖问题，利用cas法防止stock变负数。
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0) // stock > 0
                .update();
        if(!isSuccess){
            log.info("已售罄！");
            return;
        }
        // 创建订单信息
        save(voucherOrder);
    }
}
