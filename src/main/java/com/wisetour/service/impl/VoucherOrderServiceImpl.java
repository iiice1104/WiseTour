package com.wisetour.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.wisetour.dto.Result;
import com.wisetour.entity.VoucherOrder;
import com.wisetour.mapper.VoucherOrderMapper;
import com.wisetour.service.ISeckillVoucherService;
import com.wisetour.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisetour.utils.RedisIdWorker;
import com.wisetour.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.wisetour.config.KafkaConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
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

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private KafkaTemplate<String, VoucherOrder> kafkaTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final String SECKILL_TOPIC = KafkaConfig.SECKILL_TOPIC;

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    @PostConstruct //该注解：当前类初始化完毕以后立马执行
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//
//    private class VoucherOrderHandler implements Runnable{
//
//        String queueName = "stream.orders ";
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    //1.获取消息队列中订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
//                    );
//
//                    //2.判断消息获取是否成功
//                    //2.1不存在，获取失败，即没有消息
//                    if(list==null || list.isEmpty()){
//                        continue;
//                    }
//                    //2.2获取成功，去下单
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    handleVoucherOrder(voucherOrder);
//                    //3.ACK确认 XACK stream.orders g1 id
//                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
//                } catch (Exception e) {
//                    //进入pending_list
//                    log.error("处理订单异常",e);
//                    try {
//                        handlePendingList();
//                    } catch (InterruptedException ex) {
//                        throw new RuntimeException(ex);
//                    }
//                }
//            }
//        }
//
//        private void handlePendingList() throws InterruptedException {
//            while (true){
//                try {
//                    //1.获取消息队列中订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1),
//                            StreamOffset.create(queueName, ReadOffset.from("0"))
//                    );
//
//                    //2.判断消息获取是否成功
//                    //2.1不存在，获取失败，即pending_list里没有消息
//                    if(list==null || list.isEmpty()){
//                        break;
//                    }
//                    //2.2获取成功，去下单
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    handleVoucherOrder(voucherOrder);
//                    //3.ACK确认 XACK stream.orders g1 id
//                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
//                } catch (Exception e) {
//                    //进入pending_list
//                    log.error("处理pending-list订单异常",e);
////                    try {
////                        Thread.sleep(20);
////                    } catch (InterruptedException ex) {
////                        throw new RuntimeException(ex);
////                    }
//                }
//            }
//        }
//    }

    /*//阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    //线程任务(内部类),必须在秒杀前就开始执行，这样一旦有订单就可以开始写操作
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1.获取队列中订单信息
                    VoucherOrder voucherOrder = orderTasks.take();//获取并删除队头，没有则等待
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }*/

//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        //获取用户
//        Long userId = voucherOrder.getUserId();
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();//无参：不重试
//        //获取锁失败
//        if(!isLock){
//            log.error("不允许重复下单");//理论上不可能出现，因为redis已经判断过
//            return;
//        }
//        try {
//            proxy.createVoucherOrder(voucherOrder);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

    //private IVoucherOrderService proxy;

    @Override
    public Result seckkillVoucher(Long voucherId) {
        //获取用户、订单
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));
        //2.判断结果是否为0（有购买资格）
        int r = result.intValue();
        if (r!=0) {
            //2.1不为0
            return Result.fail(r==1?"库存不足":"不允许重复下单");
        }

        // 4. 为 0，有购买资格，封装订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 5. 发送订单消息到 Kafka，异步削峰
        kafkaTemplate.send(SECKILL_TOPIC, voucherOrder);

        // 6. 返回订单 ID
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckkillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //2.判断结果是否为0（有购买资格）
        int r = result.intValue();
        if (r!=0) {
            //2.1不为0
            return Result.fail(r==1?"库存不足":"不允许重复下单");
        }

        //2.2为0，有购买资格
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.4用户id
        voucherOrder.setUserId(userId);
        //2.5代金券id
        voucherOrder.setVoucherId(voucherId);

        //2.6放入阻塞队列
        orderTasks.add(voucherOrder);

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }*/

    /*@Override
    public Result seckkillVoucher(Long voucherId) {
        //查询
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始、是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {//未开始
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock <1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //intern：去字符串常量池里找，因为toString实际是new了一个对象，导致同个userId也不是同一个锁
        //将整个函数锁起来，确保订单已经提交了才释放锁
//        synchronized(userId.toString().intern()) {
//            //获得代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }

        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();//无参：不重试
        //获取锁失败
        if(!isLock){
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }*/

    /*@Transactional //因为有库存更改、订单增加，涉及两张表，所以最好加事务
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过");
        }
        //扣除库存，加乐观锁，避免超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set stock=stock-1
                .eq("voucher_id", voucherId)//优惠券
                .gt("stock", 0)//只有库存大于0才成功 while id=? and stock>0
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id

        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);//写入数据库
        //返回订单id
        return Result.ok(orderId);
    }*/
    @Override
    @Transactional //因为有库存更改、订单增加，涉及两张表，所以最好加事务
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        Long userId = voucherOrder.getUserId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //判断是否存在
        if (count > 0) {
            log.error("用户已经购买过");
            return;
        }
        //扣除库存，加乐观锁，避免超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set stock=stock-1
                .eq("voucher_id", voucherOrder.getVoucherId())//优惠券
                .gt("stock", 0)//只有库存大于0才成功 while id=? and stock>0
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        save(voucherOrder);//写入数据库
    }
}
