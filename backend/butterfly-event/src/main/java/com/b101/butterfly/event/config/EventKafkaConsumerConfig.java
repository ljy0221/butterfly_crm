package com.b101.butterfly.event.config;

import com.b101.butterfly.event.messaging.EventPayload;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/** butterfly-worker 프로세스에서만 활성화되는 event 도메인의 Kafka consumer 설정. */
@AutoConfiguration
@Profile("worker")
@EnableKafka
public class EventKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, EventPayload> eventConsumerFactory(KafkaProperties properties) {
        return new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(null));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventPayload> eventKafkaListenerContainerFactory(
            ConsumerFactory<String, EventPayload> eventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, EventPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
