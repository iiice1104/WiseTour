package com.wisetour.aspect;

import com.wisetour.annotation.RateLimit;
import com.wisetour.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    private final ConcurrentHashMap<String, RRateLimiter> LIMITER_MAP = new ConcurrentHashMap<>();

    public Object interceptor(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key();
        long rate = rateLimit.rate();
        long interval = rateLimit.interval();
        TimeUnit unit = rateLimit.unit();

        //获取或创建分布式限流器
        RRateLimiter rateLimiter = LIMITER_MAP.computeIfAbsent(key, k -> {
            RRateLimiter limiter = redissonClient.getRateLimiter(k);
            limiter.trySetRate(RateType.OVERALL, rate, interval, toRateIntervalUnit(unit));
            return limiter;
        });

        //2.尝试获取令牌
        if(rateLimiter.tryAcquire(1)){
            return joinPoint.proceed();
        }else{
            log.warn("接口 [{}] 触发限流，Key: {}", getMethodName(joinPoint), key);
            return Result.fail(rateLimit.message());
        }
    }

    /**
     * 将 TimeUnit 转换为 Redisson 的 RateIntervalUnit
     */
    private RateIntervalUnit toRateIntervalUnit(TimeUnit unit) {
        switch (unit) {
            case SECONDS: return RateIntervalUnit.SECONDS;
            case MINUTES: return RateIntervalUnit.MINUTES;
            case HOURS: return RateIntervalUnit.HOURS;
            case DAYS: return RateIntervalUnit.DAYS;
            default: return RateIntervalUnit.SECONDS;
        }
    }

    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getName();
    }
}
