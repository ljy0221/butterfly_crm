package com.b101.butterfly.event.config;

import com.b101.butterfly.event.messaging.EventPersistedPayload;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/** butterfly-worker 프로세스에서 events.persisted 토픽 publish용 Kafka producer 설정 (ADR-0014). */
@AutoConfiguration
@Profile("worker")
public class WorkerKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, EventPersistedPayload> eventPersistedProducerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, EventPersistedPayload> eventPersistedKafkaTemplate(
            ProducerFactory<String, EventPersistedPayload> eventPersistedProducerFactory
    ) {
        return new KafkaTemplate<>(eventPersistedProducerFactory);
    }
}
