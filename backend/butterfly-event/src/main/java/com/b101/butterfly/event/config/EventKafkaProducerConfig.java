package com.b101.butterfly.event.config;

import com.b101.butterfly.event.messaging.EventPayload;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/** butterfly-api 프로세스에서만 활성화되는 event 도메인의 Kafka producer 설정. */
@AutoConfiguration
@Profile("api")
public class EventKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, EventPayload> eventProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, EventPayload> eventKafkaTemplate(
            ProducerFactory<String, EventPayload> eventProducerFactory
    ) {
        return new KafkaTemplate<>(eventProducerFactory);
    }
}
