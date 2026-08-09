package com.wisetour.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String SECKILL_TOPIC = "seckill.topic";
    public static final String SECKILL_DLT = "seckill.topic.DLT";
    // Canal 推送 wisetour 库 binlog 变更的 topic
    public static final String CANAL_TOPIC = "canal.wisetour";
    // Canal 将 wisetour 库的 binlog 变更推送到此 topic
    public static final String CANAL_TOPIC = "canal.wisetour";

    @Bean
    public NewTopic seckillTopic() {
        return TopicBuilder.name(SECKILL_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seckillDlt() {
        return TopicBuilder.name(SECKILL_DLT).partitions(1).replicas(1).build();
    }

    // Canal 推送 binlog 变更的 topic，由 Canal Server 负责写入，Spring Boot 只消费
    @Bean
    public NewTopic canalTopic() {
        return TopicBuilder.name(CANAL_TOPIC).partitions(1).replicas(1).build();
    }

    // 消费失败最多重试 2 次（共 3 次），超出后将消息投递到死信 topic
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        factory.setErrorHandler(new SeekToCurrentErrorHandler(recoverer, new FixedBackOff(1000L, 2)));
        return factory;
    }
}
