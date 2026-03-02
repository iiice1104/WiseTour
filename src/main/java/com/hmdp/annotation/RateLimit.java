package com.hmdp.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    String key() default "rate:limiter";

    long capacity() default 100;

    long rate() default 50;

    long interval() default 1;

    TimeUnit unit() default TimeUnit.SECONDS;

    String message() default "系统繁忙，请稍后再试";
}
