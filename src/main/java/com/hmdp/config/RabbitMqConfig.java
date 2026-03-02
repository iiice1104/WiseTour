package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // 死信交换机和队列
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_QUEUE = "dlx.queue";
    public static final String DLX_ROUTING_KEY = "dlx.order";

    // 1. 声明普通队列，并绑定死信交换机
    @Bean
    public Queue seckillQueue() {
        Map<String, Object> args = new HashMap<>();
        // 绑定死信交换机 (Dead Letter Exchange)
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        // 绑定死信路由键 (Dead Letter Routing Key)
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return new Queue(SECKILL_QUEUE, true, false, false, args);
    }

    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Binding bindingSeckill() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    // 2. 声明死信交换机和队列
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE, true);
    }

    @Bean
    public Binding bindingDlx() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }
    // 4. 消息转换器：将 Java 对象自动转换为 JSON 格式发送，接收时自动转回对象
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
