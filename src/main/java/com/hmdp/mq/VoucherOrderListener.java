package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 监听秒杀订单队列
     * RabbitMQ 会在有消息进入 "seckill.queue" 时自动调用此方法
     */
    @RabbitListener(queues = "seckill.queue")
    public void listenSeckillOrder(VoucherOrder voucherOrder) {
        log.info("从 RabbitMQ 接收到秒杀订单，订单号: {}", voucherOrder.getId());

        // 1. 获取锁（分布式锁确保幂等性，防止数据库重复写入）
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        // 2. 尝试获取锁（非阻塞模式）
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 理论上 Lua 脚本已经过滤了重复请求，这里作为二次保险
            log.error("处理订单失败，用户下单过于频繁");
            return;
        }

        try {
            // 3. 调用 Service 执行事务方法完成下单
            // 因为是在监听器中，事务由调用 service.createVoucherOrder 触发
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("处理秒杀订单时发生异常，订单ID: {}", voucherOrder.getId(), e);
            // 抛出异常可以触发 RabbitMQ 的重试机制（取决于你的配置文件设置）
            throw e;
        } finally {
            // 4. 释放锁
            lock.unlock();
        }
    }

    /**
     * 死信队列监听
     * @param voucherOrder
     */
    @RabbitListener(queues = "dlx.queue")
    public void listenDlxOrder(VoucherOrder voucherOrder){
        log.error("【警告】发现死信订单！该订单经多次重试下单失败，需人工处理。订单ID: {}, 用户ID: {}",
                voucherOrder.getId(), voucherOrder.getUserId());
    }
}
