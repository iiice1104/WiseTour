package com.wisetour.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RateLimiterConfig {

    @Bean
    public DefaultRedisScript<Long> rateLimiterLuaScript(){
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("tokenbucket.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
