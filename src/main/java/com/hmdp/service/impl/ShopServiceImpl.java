package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//      //互斥锁解决缓存击穿
//      Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop==null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire(Long id) {
//        //1.从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //3.未命中，直接返回
//            return null;
//        }
//        //4.命中，先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //5.1.未过期，返回
//            return shop;
//        }
//        //5.2已过期，需要缓存重建
//        //6.缓存重建
//        //6.1获取锁
//        String lockKey = LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        //6.2判断
//        if(isLock){
//            //6.3成功，再次判断Redis缓存是否过期
//            String shopJsonRe = stringRedisTemplate.opsForValue().get(key);
//            RedisData redisDataRe = JSONUtil.toBean(shopJson,RedisData.class);
//            JSONObject dataRe = (JSONObject) redisData.getData();
//            shop = JSONUtil.toBean(data, Shop.class);
//            LocalDateTime expireTimeRe = redisData.getExpireTime();
//            //5.判断是否过期
//            if(expireTimeRe.isAfter(LocalDateTime.now())){
//                //5.1.未过期，返回
//                return shop;
//            }
//            // 新建一个线程执行重建过程
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    this.saveShop2Redis(id,1800L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //6.4释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//
//
//        //7.返回
//        return shop;
//    }
//
//    public Shop queryWithMutex(Long id) {
//        //1.从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //因为缓存穿透解决会存空值到redis，所以需要判断是否是空值
//        if(shopJson != null){
//            return null;
//        }
//        //4.缓存重建
//        //4.1未命中，尝试获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2判断是否获取锁
//            if(!isLock){
//                //4.3未获得则休眠一段时间再查询
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.4成功，检测Redis缓存是否存在
//            String shopJsonRe = stringRedisTemplate.opsForValue().get(key);
//            //Redis缓存 存在
//            if(StrUtil.isNotBlank(shopJsonRe)){
//                return JSONUtil.toBean(shopJsonRe,Shop.class);
//            }
//            // Redis缓存仍然不存在，则查询数据库重建缓存
//            shop = getById(id);
//            //模拟重建的延时
//            Thread.sleep(200);
//            //5.数据库也不存在，为缓解缓存穿透问题，存空值到redis，并设置TTL
//            if (shop == null) {
//                //存null到redis
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //6.存在，将其写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //7.释放互斥锁
//            unlock(lockKey);
//        }
//
//        //8.返回
//        return shop;
//    }

    //缓存穿透
//    public Shop queryWithPassThrough(Long id) {
//        //1.从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在，因为缓存穿透解决会存“”到redis，所以需要判断是否是空值
//
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //因为缓存穿透解决会存空值到redis，所以需要判断是否是空值
//        if(shopJson != null){
//            return null;
//        }
//        //Redis三种情况：有数据：确实命中；“”：命中空值，解决缓存穿透；null:确实未命中
//        //4.未命中，尝试获取互斥锁
//        //判断是否获取锁
//        //未获得则休眠一段时间再查询
//        //获得则查询数据库重建缓存
//        Shop shop = getById(id);
//        //5.数据库也不存在，为缓解缓存穿透问题，存空值到redis，并设置TTL
//        if (shop == null) {
//            //存null到redis
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //6.存在，将其写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }

    //获取锁
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    //释放锁
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    public void saveShop2Redis(Long id,Long expireSecends) throws InterruptedException {
        //1.查询店铺数据
        Shop shop=getById(id);
        //模拟重建延迟
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecends));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //缓存更新：先操作数据库，再删除缓存
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+ id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.是否需要根据坐标查询
        if(x==null || y==null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current* SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis,按照距离排序、分页
//        stringRedisTemplate.opsForGeo()
//                .search

        //解析id,查询店铺
        return null;
    }
}
