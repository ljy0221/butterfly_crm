package com.b101.butterfly.reporting.notification.config;

import com.b101.butterfly.reporting.notification.messaging.NotificationSendResultPayload;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * butterfly-worker 프로세스에서만 활성화되는 notification 도메인 Kafka consumer 설정.
 *
 * <p>EventKafkaConsumerConfig 패턴을 그대로 따른다 — 토픽별 독립 containerFactory,
 * AckMode.RECORD로 수동 커밋.
 */
@AutoConfiguration
@Profile("worker")
@EnableKafka
public class NotificationKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, NotificationSendResultPayload> notificationSendResultConsumerFactory(
            KafkaProperties properties) {
        return new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(null));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationSendResultPayload>
    notificationSendResultListenerContainerFactory(
            ConsumerFactory<String, NotificationSendResultPayload> notificationSendResultConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationSendResultPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationSendResultConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
